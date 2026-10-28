-- App Pet - hàm (RPC), trigger, RLS, quyền, Realtime, Storage. Chạy SAU 001_schema.sql.
-- Nguyên tắc: app chỉ dùng anon key => mọi quy tắc nằm ở đây. Ghi vào chat/cộng đồng CHỈ qua hàm SECURITY DEFINER.

-- ============================== TRIGGER ==============================
create or replace function public.set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end $$;

drop trigger if exists trg_pets_updated on public.pets;
create trigger trg_pets_updated before update on public.pets
  for each row execute function public.set_updated_at();
drop trigger if exists trg_profiles_updated on public.profiles;
create trigger trg_profiles_updated before update on public.profiles
  for each row execute function public.set_updated_at();
drop trigger if exists trg_gallery_updated on public.gallery_images;
create trigger trg_gallery_updated before update on public.gallery_images
  for each row execute function public.set_updated_at();

-- Tạo profile khi có tài khoản mới. username/display_name lấy từ "data" lúc signup.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_username text := lower(btrim(coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1))));
begin
  insert into public.profiles (id, username, display_name)
  values (new.id, v_username, coalesce(nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''), v_username));
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================== HÀM TIỆN ÍCH ==============================
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'ADMIN');
$$;

create or replace function public.is_member(p_community_id bigint) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.community_members m
                 where m.community_id = p_community_id and m.user_id = auth.uid() and m.status <> 'BANNED');
$$;

-- Đăng ký: kiểm tra tên đăng nhập còn trống (ai cũng gọi được, chỉ trả về true/false).
create or replace function public.username_available(p_username text) returns boolean
language sql stable security definer set search_path = '' as $$
  select not exists (select 1 from public.profiles where lower(username) = lower(btrim(p_username)));
$$;

-- Đăng nhập bằng tên đăng nhập: trả về email của tài khoản. LƯU Ý BẢO MẬT: ai biết username cũng lấy được email.
-- Muốn chặt hơn: xóa hàm này và chỉ cho đăng nhập bằng email (đặt Config.ALLOW_USERNAME_LOGIN = false trong app).
create or replace function public.login_email(p_username text) returns text
language sql stable security definer set search_path = '' as $$
  select u.email::text from auth.users u join public.profiles p on p.id = u.id
  where lower(p.username) = lower(btrim(p_username)) limit 1;
$$;

-- ============================== ẢNH THÚ CƯNG ==============================
create or replace function public.set_pet_main_image(p_pet_id bigint, p_image_url text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare v_id bigint;
begin
  if not exists (select 1 from public.pets where id = p_pet_id and user_id = auth.uid()) then
    raise exception 'not_owner';
  end if;
  update public.pet_images set is_main = false where pet_id = p_pet_id and is_main;
  insert into public.pet_images (pet_id, image_url, is_main) values (p_pet_id, p_image_url, true)
  returning id into v_id;
  return v_id;
end $$;

create or replace function public.link_recognition(p_recognition_id bigint, p_pet_image_id bigint) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  update public.recognition_results r set pet_image_id = p_pet_image_id
  where r.id = p_recognition_id and r.user_id = auth.uid() and r.pet_image_id is null
    and exists (select 1 from public.pet_images i join public.pets p on p.id = i.pet_id
                where i.id = p_pet_image_id and p.user_id = auth.uid());
  return found;
end $$;

-- ============================== CỘNG ĐỒNG / CHAT ==============================
create or replace function public.my_membership(p_community_id bigint)
returns table (role text, status text, muted_until timestamptz, can_send boolean)
language sql stable security definer set search_path = '' as $$
  select m.role, m.status, m.muted_until,
         (m.status = 'ACTIVE' or (m.status = 'MUTED' and m.muted_until is not null and m.muted_until <= now()))
  from public.community_members m
  where m.community_id = p_community_id and m.user_id = auth.uid();
$$;

-- Cho người dùng vào cộng đồng của mọi loài họ đang nuôi (tạo cộng đồng nếu loài đó chưa có).
create or replace function public.join_pet_communities() returns void
language plpgsql security definer set search_path = '' as $$
declare
  r record;
  v_cid bigint;
  v_name text;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  for r in select distinct p.pet_type_id from public.pets p where p.user_id = auth.uid() loop
    select c.id into v_cid from public.communities c where c.pet_type_id = r.pet_type_id order by c.id limit 1;
    if v_cid is null then
      select t.name into v_name from public.pet_types t where t.id = r.pet_type_id;
      insert into public.communities (pet_type_id, name, description)
      values (r.pet_type_id, 'Cộng đồng ' || v_name,
              'Nơi những người nuôi ' || lower(v_name) || ' cùng trò chuyện và chia sẻ kinh nghiệm.')
      returning id into v_cid;
    end if;
    insert into public.community_members (community_id, user_id) values (v_cid, auth.uid())
    on conflict do nothing;
  end loop;
end $$;

create or replace function public.my_communities()
returns table (id bigint, pet_type_id integer, name text, description text,
               member_count bigint, last_message text, last_message_at timestamptz)
language plpgsql security definer set search_path = '' as $$
begin
  perform public.join_pet_communities();
  return query
  select c.id, c.pet_type_id, c.name, c.description,
         (select count(*) from public.community_members m where m.community_id = c.id and m.status <> 'BANNED'),
         (select x.content from public.messages x where x.community_id = c.id and not x.is_deleted order by x.id desc limit 1),
         (select x.created_at from public.messages x where x.community_id = c.id and not x.is_deleted order by x.id desc limit 1)
  from public.communities c
  join public.community_members cm on cm.community_id = c.id and cm.user_id = auth.uid() and cm.status <> 'BANNED'
  order by c.id;
end $$;

-- Tin nhắn: p_since = null -> p_limit tin gần nhất; ngược lại chỉ tin MỚI hoặc VỪA ĐỔI (xóa) sau mốc p_since.
create or replace function public.get_messages(p_community_id bigint, p_since timestamptz default null, p_limit integer default 100)
returns table (id bigint, sender_id uuid, sender_name text, content text,
               created_at timestamptz, changed_at timestamptz, is_deleted boolean)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_member(p_community_id) then raise exception 'not_member'; end if;
  if p_since is null then
    return query
    select s.id, s.sender_id, s.sender_name, s.content, s.created_at, s.changed_at, s.is_deleted
    from (select m.id, m.sender_id,
                 coalesce(pr.display_name, pr.username, 'Trợ lý Pet')::text as sender_name,
                 case when m.is_deleted then null else m.content end as content,
                 m.created_at, m.changed_at, m.is_deleted
          from public.messages m left join public.profiles pr on pr.id = m.sender_id
          where m.community_id = p_community_id
          order by m.id desc limit least(greatest(p_limit, 1), 200)) s
    order by s.id;
  else
    return query
    select m.id, m.sender_id,
           coalesce(pr.display_name, pr.username, 'Trợ lý Pet')::text,
           case when m.is_deleted then null else m.content end,
           m.created_at, m.changed_at, m.is_deleted
    from public.messages m left join public.profiles pr on pr.id = m.sender_id
    where m.community_id = p_community_id and m.changed_at > p_since
    order by m.id limit 500;
  end if;
end $$;

create or replace function public.send_message(p_community_id bigint, p_content text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  v_mem record;
  v_id bigint;
  v_text text := btrim(coalesce(p_content, ''));
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if v_text = '' then raise exception 'empty_message'; end if;
  if char_length(v_text) > 2000 then raise exception 'message_too_long'; end if;
  select * into v_mem from public.community_members
   where community_id = p_community_id and user_id = auth.uid();
  if not found or v_mem.status = 'BANNED' then raise exception 'not_member'; end if;
  if v_mem.status = 'MUTED' then
    if v_mem.muted_until is null or v_mem.muted_until > now() then raise exception 'muted'; end if;
    update public.community_members set status = 'ACTIVE', muted_until = null
     where community_id = p_community_id and user_id = auth.uid();
  end if;
  if (select count(*) from public.messages
       where sender_id = auth.uid() and created_at > now() - interval '1 minute') >= 30 then
    raise exception 'rate_limited';
  end if;
  insert into public.messages (community_id, sender_id, content)
  values (p_community_id, auth.uid(), v_text) returning id into v_id;
  return v_id;
end $$;

-- Chỉ chủ tin, MOD/ADMIN của nhóm hoặc ADMIN hệ thống được xóa. Nội dung bị xóa hẳn (không giữ bản sao).
create or replace function public.delete_message(p_message_id bigint) returns boolean
language plpgsql security definer set search_path = '' as $$
declare
  v_msg record;
  v_mem record;
  v_ok boolean := false;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into v_msg from public.messages where id = p_message_id and not is_deleted;
  if not found then return false; end if;
  if v_msg.sender_id is not distinct from auth.uid() then
    v_ok := true;
  else
    select * into v_mem from public.community_members
     where community_id = v_msg.community_id and user_id = auth.uid();
    if found and v_mem.status <> 'BANNED' and v_mem.role in ('MOD','ADMIN') then v_ok := true; end if;
    if not v_ok and public.is_admin() then v_ok := true; end if;
  end if;
  if not v_ok then return false; end if;
  update public.messages
     set is_deleted = true, content = null, image_url = null,
         deleted_at = now(), deleted_by = auth.uid(), changed_at = now(),
         delete_reason = case when v_msg.sender_id is not distinct from auth.uid()
                              then 'Người gửi tự xóa' else 'Bị kiểm duyệt' end
   where id = p_message_id;
  return true;
end $$;

create or replace function public.report_message(p_message_id bigint, p_reason text, p_detail text default null) returns boolean
language plpgsql security definer set search_path = '' as $$
declare v_msg record;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into v_msg from public.messages where id = p_message_id and not is_deleted;
  if not found or v_msg.sender_id is not distinct from auth.uid() then return false; end if;
  if not public.is_member(v_msg.community_id) then return false; end if;
  insert into public.message_reports (message_id, reporter_id, reason, detail)
  values (p_message_id, auth.uid(), p_reason, left(p_detail, 500))
  on conflict (message_id, reporter_id) do nothing;
  return found;
end $$;

-- ============================== QUYỀN GỌI HÀM ==============================
-- Mặc định Postgres cho PUBLIC (kể cả anon) gọi hàm: thu hồi rồi chỉ cấp lại cho đúng vai trò.
revoke all on function public.is_admin() from public;
revoke all on function public.is_member(bigint) from public, anon;
revoke all on function public.set_pet_main_image(bigint, text) from public, anon;
revoke all on function public.link_recognition(bigint, bigint) from public, anon;
revoke all on function public.my_membership(bigint) from public, anon;
revoke all on function public.join_pet_communities() from public, anon;
revoke all on function public.my_communities() from public, anon;
revoke all on function public.get_messages(bigint, timestamptz, integer) from public, anon;
revoke all on function public.send_message(bigint, text) from public, anon;
revoke all on function public.delete_message(bigint) from public, anon;
revoke all on function public.report_message(bigint, text, text) from public, anon;
-- is_admin() được dùng trong policy của gallery_images (anon cũng đọc được) nên anon phải gọi được; nó chỉ trả true/false.
grant execute on function public.is_admin() to anon, authenticated;
grant execute on function public.is_member(bigint) to authenticated;
grant execute on function public.set_pet_main_image(bigint, text) to authenticated;
grant execute on function public.link_recognition(bigint, bigint) to authenticated;
grant execute on function public.my_membership(bigint) to authenticated;
grant execute on function public.join_pet_communities() to authenticated;
grant execute on function public.my_communities() to authenticated;
grant execute on function public.get_messages(bigint, timestamptz, integer) to authenticated;
grant execute on function public.send_message(bigint, text) to authenticated;
grant execute on function public.delete_message(bigint) to authenticated;
grant execute on function public.report_message(bigint, text, text) to authenticated;
-- Hai hàm dành cho màn đăng nhập/đăng ký (chưa có tài khoản) nên anon phải gọi được:
grant execute on function public.username_available(text) to anon, authenticated;
grant execute on function public.login_email(text) to anon, authenticated;

-- ============================== RLS ==============================
alter table public.profiles enable row level security;
alter table public.pet_types enable row level security;
alter table public.breeds enable row level security;
alter table public.breed_aliases enable row level security;
alter table public.pets enable row level security;
alter table public.pet_images enable row level security;
alter table public.recognition_results enable row level security;
alter table public.gallery_images enable row level security;
alter table public.communities enable row level security;
alter table public.community_members enable row level security;
alter table public.messages enable row level security;
alter table public.message_reports enable row level security;

-- Catalog: ai cũng đọc được, chỉ service_role (script) ghi được.
drop policy if exists catalog_read on public.pet_types;
create policy catalog_read on public.pet_types for select to anon, authenticated using (true);
drop policy if exists catalog_read on public.breeds;
create policy catalog_read on public.breeds for select to anon, authenticated using (true);
drop policy if exists catalog_read on public.breed_aliases;
create policy catalog_read on public.breed_aliases for select to anon, authenticated using (true);

-- Profiles: người đăng nhập xem được tên của nhau; chỉ sửa được hồ sơ của mình, và chỉ 2 cột display_name/avatar_url.
drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select to authenticated using (true);
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
revoke update on public.profiles from anon, authenticated;
grant update (display_name, avatar_url) on public.profiles to authenticated;

-- Pets: chủ sở hữu toàn quyền, người khác không thấy.
drop policy if exists pets_owner on public.pets;
create policy pets_owner on public.pets for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Pet images: đọc/xóa nếu là chủ thú cưng; thêm ảnh chỉ qua set_pet_main_image().
drop policy if exists pet_images_read on public.pet_images;
create policy pet_images_read on public.pet_images for select to authenticated
  using (exists (select 1 from public.pets p where p.id = pet_id and p.user_id = (select auth.uid())));
drop policy if exists pet_images_delete on public.pet_images;
create policy pet_images_delete on public.pet_images for delete to authenticated
  using (exists (select 1 from public.pets p where p.id = pet_id and p.user_id = (select auth.uid())));
revoke insert, update on public.pet_images from anon, authenticated;

-- Recognition: ghi/đọc của chính mình; gắn vào ảnh qua link_recognition().
drop policy if exists recognition_own_read on public.recognition_results;
create policy recognition_own_read on public.recognition_results for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists recognition_own_insert on public.recognition_results;
create policy recognition_own_insert on public.recognition_results for insert to authenticated
  with check (user_id = (select auth.uid()) and pet_image_id is null);
revoke update, delete on public.recognition_results from anon, authenticated;

-- Gallery: ai cũng xem được ảnh đã duyệt; người dùng chỉ được gửi ảnh ở trạng thái PENDING; admin duyệt.
drop policy if exists gallery_read on public.gallery_images;
create policy gallery_read on public.gallery_images for select to anon, authenticated
  using (status = 'APPROVED' or public.is_admin());
drop policy if exists gallery_contribute on public.gallery_images;
create policy gallery_contribute on public.gallery_images for insert to authenticated
  with check (source_type = 'USER' and status = 'PENDING' and contributor_id = (select auth.uid()));
drop policy if exists gallery_admin_update on public.gallery_images;
create policy gallery_admin_update on public.gallery_images for update to authenticated
  using (public.is_admin()) with check (public.is_admin());
drop policy if exists gallery_admin_delete on public.gallery_images;
create policy gallery_admin_delete on public.gallery_images for delete to authenticated using (public.is_admin());

-- Cộng đồng / thành viên / tin nhắn / báo cáo: chỉ đọc, mọi thay đổi qua hàm ở trên.
drop policy if exists communities_read on public.communities;
create policy communities_read on public.communities for select to authenticated using (true);
drop policy if exists members_read_own on public.community_members;
create policy members_read_own on public.community_members for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists messages_read_members on public.messages;
create policy messages_read_members on public.messages for select to authenticated
  using (public.is_member(community_id));
drop policy if exists reports_read_own on public.message_reports;
create policy reports_read_own on public.message_reports for select to authenticated
  using (reporter_id = (select auth.uid()) or public.is_admin());
revoke insert, update, delete on public.communities from anon, authenticated;
revoke insert, update, delete on public.community_members from anon, authenticated;
revoke insert, update, delete on public.messages from anon, authenticated;
revoke insert, update, delete on public.message_reports from anon, authenticated;

-- ============================== REALTIME ==============================
-- Để app được báo ngay khi có tin mới (RLS ở trên vẫn áp dụng: chỉ thành viên nhận được).
do $$
begin
  alter publication supabase_realtime add table public.messages;
exception
  when duplicate_object then null;
  when undefined_object then null;
end $$;

-- ============================== STORAGE (ảnh đại diện) ==============================
-- Bucket public (đọc không cần đăng nhập); ghi chỉ vào thư mục có tên = id của chính mình: <uid>/<tên file>.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set public = excluded.public, file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists avatars_delete_own on storage.objects;
create policy avatars_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
