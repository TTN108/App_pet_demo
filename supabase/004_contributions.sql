-- App Pet - đóng góp của người dùng: ảnh (tải lên / từ camera), bài wiki sinh từ chat, kiểm duyệt. Chạy SAU 003.
-- Nguyên tắc: mọi đóng góp vào trạng thái PENDING, chỉ ADMIN duyệt mới hiển thị công khai. App chỉ gọi các hàm dưới đây.

-- ============================== ẢNH ĐÓNG GÓP ==============================
alter table public.gallery_images add column if not exists origin text
  check (origin is null or origin in ('UPLOAD','CAMERA'));
alter table public.gallery_images add column if not exists recognition_id bigint
  references public.recognition_results(id) on delete set null;
-- Với ảnh từ camera: true nếu người dùng đã ĐỔI giống so với dự đoán của model (ví dụ khó, rất có giá trị để huấn luyện lại).
alter table public.gallery_images add column if not exists label_changed boolean;

-- Bucket cho ảnh đóng góp: đọc công khai theo URL không đoán được (uuid), ghi chỉ vào thư mục của chính mình.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('contrib', 'contrib', true, 3145728, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set public = excluded.public, file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists contrib_insert_own on storage.objects;
create policy contrib_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'contrib' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists contrib_delete_own on storage.objects;
create policy contrib_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'contrib' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Không cho app INSERT thẳng vào gallery_images nữa (tránh nhét URL tùy ý): chỉ qua contribute_gallery_image().
drop policy if exists gallery_contribute on public.gallery_images;
revoke insert on public.gallery_images from anon, authenticated;
-- Người đóng góp xem được ảnh của mình (kể cả đang chờ duyệt / bị từ chối).
drop policy if exists gallery_read on public.gallery_images;
create policy gallery_read on public.gallery_images for select to anon, authenticated
  using (status = 'APPROVED' or contributor_id = (select auth.uid()) or public.is_admin());

create or replace function public.contribute_gallery_image(
  p_pet_type_id integer, p_breed_id integer, p_image_url text, p_origin text,
  p_recognition_id bigint default null, p_title text default null) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  v_id bigint;
  v_rec bigint := null;
  v_det integer;
  v_changed boolean := null;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_origin is null or p_origin not in ('UPLOAD','CAMERA') then raise exception 'bad_origin'; end if;
  -- URL phải nằm trong thư mục của chính người gửi ở bucket contrib
  if p_image_url is null
     or position('/storage/v1/object/public/contrib/' || auth.uid()::text || '/' in p_image_url) = 0 then
    raise exception 'bad_image_url';
  end if;
  if not exists (select 1 from public.breeds where id = p_breed_id and pet_type_id = p_pet_type_id) then
    raise exception 'bad_breed';
  end if;
  if (select count(*) from public.gallery_images
       where contributor_id = auth.uid() and status = 'PENDING') >= 20 then
    raise exception 'too_many_pending';
  end if;
  if p_recognition_id is not null then
    select r.id, r.detected_breed_id into v_rec, v_det
      from public.recognition_results r where r.id = p_recognition_id and r.user_id = auth.uid();
    if found then v_changed := v_det is distinct from p_breed_id; else v_rec := null; end if;
  end if;
  insert into public.gallery_images
    (pet_type_id, breed_id, source_type, external_id, contributor_id, status, image_path, title,
     origin, recognition_id, label_changed)
  values
    (p_pet_type_id, p_breed_id, 'USER', gen_random_uuid()::text, auth.uid(), 'PENDING', p_image_url,
     left(btrim(p_title), 100), p_origin, v_rec, v_changed)
  returning id into v_id;
  return v_id;
end $$;

-- Rút lại ảnh đang chờ duyệt của chính mình.
create or replace function public.withdraw_contribution(p_id bigint) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.gallery_images
   where id = p_id and contributor_id = auth.uid() and status = 'PENDING';
  return found;
end $$;

create or replace function public.moderate_gallery_image(p_id bigint, p_approve boolean, p_reason text default null) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  update public.gallery_images
     set status = case when p_approve then 'APPROVED' else 'REJECTED' end,
         reviewed_by = auth.uid(), reviewed_at = now(),
         reject_reason = case when p_approve then null else left(p_reason, 200) end
   where id = p_id and status = 'PENDING';
  return found;
end $$;

-- ============================== WIKI TỪ CỘNG ĐỒNG ==============================
create table if not exists public.community_wiki (
  id bigint generated always as identity primary key,
  pet_type_id integer not null references public.pet_types(id),
  breed_id integer,
  category_id integer not null check (category_id between 1 and 6),   -- khớp danh mục wiki trong app (tools/breed_meta.py)
  title text not null check (char_length(title) between 5 and 120),
  summary text,
  content text not null check (char_length(content) between 20 and 4000),
  source_community_id bigint references public.communities(id) on delete set null,
  source_message_id bigint references public.messages(id) on delete set null,
  author_id uuid references public.profiles(id) on delete set null,
  show_author boolean not null default false,
  status text not null default 'PENDING' check (status in ('PENDING','PUBLISHED','REJECTED')),
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  reject_reason text,
  created_at timestamptz not null default now(),
  foreign key (breed_id, pet_type_id) references public.breeds(id, pet_type_id)
);
create index if not exists idx_cwiki_status_type on public.community_wiki(status, pet_type_id, id);

alter table public.community_wiki enable row level security;
-- Đọc trực tiếp bảng chỉ dành cho tác giả và admin; mọi người khác đọc qua list_community_wiki() (ẩn danh tác giả).
drop policy if exists cwiki_read_own_or_admin on public.community_wiki;
create policy cwiki_read_own_or_admin on public.community_wiki for select to authenticated
  using (author_id = (select auth.uid()) or public.is_admin());
revoke insert, update, delete on public.community_wiki from anon, authenticated;

-- Đề xuất đưa một đoạn trao đổi trong chat thành bài wiki (người đề xuất được sửa nội dung trước khi gửi).
create or replace function public.propose_wiki_from_message(
  p_message_id bigint, p_title text, p_category_id integer, p_content text,
  p_breed_id integer default null, p_show_author boolean default false) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  v_msg record;
  v_type integer;
  v_title text := btrim(coalesce(p_title, ''));
  v_content text := btrim(coalesce(p_content, ''));
  v_id bigint;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into v_msg from public.messages where id = p_message_id and not is_deleted;
  if not found then raise exception 'message_not_found'; end if;
  if not public.is_member(v_msg.community_id) then raise exception 'not_member'; end if;
  select c.pet_type_id into v_type from public.communities c where c.id = v_msg.community_id;
  if v_type is null then raise exception 'no_pet_type'; end if;
  if char_length(v_title) < 5 or char_length(v_title) > 120 then raise exception 'bad_title'; end if;
  if char_length(v_content) < 20 or char_length(v_content) > 4000 then raise exception 'bad_content'; end if;
  if p_category_id is null or p_category_id not between 1 and 6 then raise exception 'bad_category'; end if;
  if p_breed_id is not null and not exists
       (select 1 from public.breeds where id = p_breed_id and pet_type_id = v_type) then
    raise exception 'bad_breed';
  end if;
  if (select count(*) from public.community_wiki where author_id = auth.uid() and status = 'PENDING') >= 10 then
    raise exception 'too_many_pending';
  end if;
  insert into public.community_wiki
    (pet_type_id, breed_id, category_id, title, summary, content, source_community_id, source_message_id,
     author_id, show_author)
  values
    (v_type, p_breed_id, p_category_id, v_title, left(regexp_replace(v_content, '\s+', ' ', 'g'), 200), v_content,
     v_msg.community_id, p_message_id, auth.uid(), coalesce(p_show_author, false))
  returning id into v_id;
  return v_id;
end $$;

create or replace function public.moderate_wiki(p_id bigint, p_approve boolean, p_reason text default null) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  update public.community_wiki
     set status = case when p_approve then 'PUBLISHED' else 'REJECTED' end,
         reviewed_by = auth.uid(), reviewed_at = now(),
         reject_reason = case when p_approve then null else left(p_reason, 200) end
   where id = p_id and status = 'PENDING';
  return found;
end $$;

-- Danh sách bài đã duyệt (tên tác giả chỉ hiện nếu người đề xuất đồng ý).
create or replace function public.list_community_wiki(
  p_pet_type_id integer default null, p_breed_id integer default null,
  p_query text default null, p_limit integer default 50)
returns table (id bigint, pet_type_id integer, breed_id integer, category_id integer, title text,
               summary text, content text, author_name text, created_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select w.id, w.pet_type_id, w.breed_id, w.category_id, w.title, w.summary, w.content,
         case when w.show_author then pr.display_name end, w.created_at
  from public.community_wiki w left join public.profiles pr on pr.id = w.author_id
  where w.status = 'PUBLISHED'
    and (p_pet_type_id is null or w.pet_type_id = p_pet_type_id)
    and (p_breed_id is null or w.breed_id = p_breed_id or w.breed_id is null)
    and (p_query is null or btrim(p_query) = '' or w.title ilike '%' || btrim(p_query) || '%'
         or w.content ilike '%' || btrim(p_query) || '%')
  order by case when w.breed_id = p_breed_id then 0 else 1 end, w.id desc
  limit least(greatest(coalesce(p_limit, 50), 1), 100);
$$;

create or replace function public.get_community_wiki(p_id bigint)
returns table (id bigint, pet_type_id integer, breed_id integer, category_id integer, title text,
               summary text, content text, author_name text, created_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select w.id, w.pet_type_id, w.breed_id, w.category_id, w.title, w.summary, w.content,
         case when w.show_author then pr.display_name end, w.created_at
  from public.community_wiki w left join public.profiles pr on pr.id = w.author_id
  where w.id = p_id and w.status = 'PUBLISHED';
$$;

-- ============================== BÁO CÁO CHAT (admin) ==============================
create or replace function public.admin_reports()
returns table (report_id bigint, message_id bigint, reason text, detail text, content text,
               sender_name text, reporter_name text, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return query
  select r.id, r.message_id, r.reason, r.detail, m.content,
         coalesce(sp.display_name, sp.username, 'Trợ lý Pet')::text,
         coalesce(rp.display_name, rp.username)::text, r.created_at
  from public.message_reports r
  join public.messages m on m.id = r.message_id
  left join public.profiles sp on sp.id = m.sender_id
  left join public.profiles rp on rp.id = r.reporter_id
  where r.status = 'PENDING' and not m.is_deleted
  order by r.id limit 100;
end $$;

-- p_violation = true: xác nhận vi phạm và xóa tin; false: bỏ qua báo cáo.
create or replace function public.resolve_report(p_report_id bigint, p_violation boolean) returns boolean
language plpgsql security definer set search_path = '' as $$
declare v_rep record;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  select * into v_rep from public.message_reports where id = p_report_id and status = 'PENDING';
  if not found then return false; end if;
  if p_violation then perform public.delete_message(v_rep.message_id); end if;
  update public.message_reports
     set status = case when p_violation then 'VIOLATION' else 'DISMISSED' end,
         handled_by = auth.uid(), handled_at = now(),
         action_taken = case when p_violation then 'DELETED_MESSAGE' else 'NONE' end
   where id = p_report_id;
  return true;
end $$;

-- ============================== QUYỀN GỌI HÀM ==============================
revoke all on function public.contribute_gallery_image(integer, integer, text, text, bigint, text) from public, anon;
revoke all on function public.withdraw_contribution(bigint) from public, anon;
revoke all on function public.moderate_gallery_image(bigint, boolean, text) from public, anon;
revoke all on function public.propose_wiki_from_message(bigint, text, integer, text, integer, boolean) from public, anon;
revoke all on function public.moderate_wiki(bigint, boolean, text) from public, anon;
revoke all on function public.list_community_wiki(integer, integer, text, integer) from public, anon;
revoke all on function public.get_community_wiki(bigint) from public, anon;
revoke all on function public.admin_reports() from public, anon;
revoke all on function public.resolve_report(bigint, boolean) from public, anon;
grant execute on function public.contribute_gallery_image(integer, integer, text, text, bigint, text) to authenticated;
grant execute on function public.withdraw_contribution(bigint) to authenticated;
grant execute on function public.moderate_gallery_image(bigint, boolean, text) to authenticated;
grant execute on function public.propose_wiki_from_message(bigint, text, integer, text, integer, boolean) to authenticated;
grant execute on function public.moderate_wiki(bigint, boolean, text) to authenticated;
grant execute on function public.list_community_wiki(integer, integer, text, integer) to authenticated;
grant execute on function public.get_community_wiki(bigint) to authenticated;
grant execute on function public.admin_reports() to authenticated;
grant execute on function public.resolve_report(bigint, boolean) to authenticated;
