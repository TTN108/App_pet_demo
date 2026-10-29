-- App Pet v6 - chỉ dùng Supabase (bỏ ImageKit). Chạy SAU 004. Chạy lại được.
-- Ảnh bộ sưu tập nằm trong bucket public "gallery" (ảnh đầy đủ + ảnh nhỏ tạo sẵn, vì gói free KHÔNG có tính năng
-- thu nhỏ ảnh theo yêu cầu). gallery_images.image_path / thumb_path là đường dẫn trong bucket (ảnh cào) hoặc URL tuyệt đối
-- (ảnh người dùng đóng góp nằm ở bucket contrib).

alter table public.gallery_images add column if not exists thumb_path text;

-- Bucket gallery: chỉ script chạy bằng service_role ghi được (service_role bỏ qua RLS); mọi người đọc công khai theo URL.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('gallery', 'gallery', true, 2097152, array['image/jpeg','image/webp'])
on conflict (id) do update
  set public = excluded.public, file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- contribute_gallery_image nhận thêm ảnh nhỏ (p_thumb_url). Đổi chữ ký hàm nên phải xóa bản cũ.
drop function if exists public.contribute_gallery_image(integer, integer, text, text, bigint, text);

create or replace function public.contribute_gallery_image(
  p_pet_type_id integer, p_breed_id integer, p_image_url text, p_origin text,
  p_recognition_id bigint default null, p_title text default null, p_thumb_url text default null) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  v_id bigint;
  v_rec bigint := null;
  v_det integer;
  v_changed boolean := null;
  v_prefix text := '/storage/v1/object/public/contrib/' || coalesce(auth.uid()::text, '') || '/';
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_origin is null or p_origin not in ('UPLOAD','CAMERA') then raise exception 'bad_origin'; end if;
  -- cả ảnh đầy đủ và ảnh nhỏ phải nằm trong thư mục của chính người gửi ở bucket contrib
  if p_image_url is null or position(v_prefix in p_image_url) = 0 then raise exception 'bad_image_url'; end if;
  if p_thumb_url is not null and position(v_prefix in p_thumb_url) = 0 then raise exception 'bad_image_url'; end if;
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
    (pet_type_id, breed_id, source_type, external_id, contributor_id, status, image_path, thumb_path, title,
     origin, recognition_id, label_changed)
  values
    (p_pet_type_id, p_breed_id, 'USER', gen_random_uuid()::text, auth.uid(), 'PENDING', p_image_url, p_thumb_url,
     left(btrim(p_title), 100), p_origin, v_rec, v_changed)
  returning id into v_id;
  return v_id;
end $$;

revoke all on function public.contribute_gallery_image(integer, integer, text, text, bigint, text, text) from public, anon;
grant execute on function public.contribute_gallery_image(integer, integer, text, text, bigint, text, text) to authenticated;
