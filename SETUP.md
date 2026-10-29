# App Pet v6 - chỉ dùng Supabase

## Kiến trúc
| Dữ liệu | Nơi lưu |
|---|---|
| Tài khoản, đăng nhập | Supabase Auth |
| Hồ sơ, thú cưng, chat, báo cáo, thông tin ảnh bộ sưu tập, đóng góp | Supabase Postgres (+ RLS) |
| Ảnh đại diện / ảnh thú cưng | Storage, bucket `avatars` (public, ghi vào thư mục của chính mình) |
| Ảnh bộ sưu tập (ảnh cào) | Storage, bucket `gallery` (public; chỉ script service_role ghi) |
| Ảnh người dùng đóng góp | Storage, bucket `contrib` (public theo URL uuid; chỉ hiện trong app sau khi admin duyệt) |
| Loài, giống, alias nhận diện, wiki có sẵn | Trong app (SQLite, dùng được khi không có mạng) |
| Wiki do cộng đồng đề xuất (đã duyệt) | Supabase (`community_wiki`) |
| Model nhận diện giống | Trong app (TFLite, chạy trên máy) |

Gói free của Supabase KHÔNG tự thu nhỏ ảnh (tính năng đó chỉ có từ gói Pro), nên mỗi ảnh được lưu 2 bản tạo sẵn:
ảnh đầy đủ (cạnh dài <= 1024px) và ảnh nhỏ (<= 360px). Lưới ảnh chỉ tải ảnh nhỏ; ảnh đầy đủ chỉ tải khi xem lớn hoặc tải về.
Giới hạn gói free cần để ý: 1 GB lưu file, 5 GB băng thông, project bị tạm dừng sau 1 tuần không hoạt động.

ID loài/giống GIỐNG HỆT giữa app và Supabase (xem `tools/breed_meta.py`). Muốn thêm loài/giống chỉ thêm vào CUỐI danh sách.

## 1. Supabase
1. Tạo project. SQL Editor chạy lần lượt: `supabase/001_schema.sql` -> `002_security.sql` -> `003_seed_catalog.sql` -> `004_contributions.sql` -> `005_supabase_only.sql`.
2. Authentication > Providers > Email: khi demo có thể tắt "Confirm email" (email xác nhận mặc định bị giới hạn số lượng gửi rất thấp).
3. Project Settings > API: lấy `Project URL` và `anon public key`.
4. Tài khoản quản trị: `update public.profiles set role = 'ADMIN' where username = 'ten_cua_ban';`
5. KHÔNG bao giờ đưa `service_role key` vào app.

## 2. Cấu hình app
Sao chép `local.properties.example` thành `local.properties`, điền `SUPABASE_URL` và `SUPABASE_ANON_KEY`, build lại app.

## 3. Dữ liệu (thư mục `tools/`)
```bash
pip install requests pillow beautifulsoup4 lxml
python download_pet_images.py --per-breed 40 --out pet               # thu thập ảnh + manifest.csv
set SUPABASE_URL=https://xxx.supabase.co
set SUPABASE_SERVICE_ROLE_KEY=eyJ...                                 # chỉ trên máy bạn
python upload_storage.py --pet-dir pet --dry-run                     # ước lượng dung lượng trước (không tải lên)
python upload_storage.py --pet-dir pet                               # tạo ảnh đầy đủ + ảnh nhỏ, tải lên bucket gallery
python import_supabase.py --pet-dir pet                              # ghi gallery_images (image_path, thumb_path)
python crawl_care_wiki.py --source wikipedia --out wiki_out          # bài wiki
python build_catalog.py --wiki wiki_out/wiki_articles.jsonl --check  # catalog trong app + seed Supabase
python export_training_set.py --out train_data                       # ảnh ĐÃ DUYỆT -> bộ dữ liệu huấn luyện
python train_breed_classifier.py --pet-dir train_data                # model nhận diện (cần TensorFlow; có thể chạy trên Kaggle)
```
`upload_storage.py` có chốt chặn `--budget-mb` (mặc định 900 MB) để không vượt giới hạn 1 GB. Muốn chứa nhiều ảnh hơn: giảm
`--full-size` (vd 800) và `--quality` (vd 72).

## 4. Đóng góp của người dùng
| Luồng | Cách hoạt động |
|---|---|
| Wiki từ chat | Nhấn giữ một tin nhắn > "Đề xuất đưa vào wiki": người dùng sửa tiêu đề/nội dung, chọn danh mục, quyết định có hiện tên mình hay không. Bài vào trạng thái PENDING, ADMIN duyệt thì hiện trong mục Wiki. Không có chuyện app tự cào toàn bộ chat. |
| Ảnh người dùng tải lên | Bộ sưu tập > "+ Ảnh" (chọn loài và giống ở bộ lọc trước) > đồng ý điều khoản > chọn ảnh. |
| Ảnh từ camera | Chụp ảnh nhận diện > chọn đúng giống > "Đóng góp ảnh này cho bộ sưu tập". Server ghi lại việc người dùng có ĐỔI giống so với dự đoán của model (`label_changed`) = ví dụ khó. |
| Gửi ảnh | WorkManager xếp hàng, tự gửi khi có mạng, thử lại nếu lỗi tạm thời. App tự tạo ảnh nhỏ, xóa EXIF (kể cả vị trí) trước khi gửi. |
| Duyệt | Tài khoản ADMIN thấy "Quản trị nội dung" trong Hồ sơ: duyệt/từ chối ảnh, bài wiki, xử lý báo cáo chat. |

## 5. Dữ liệu nào lên Supabase, dữ liệu nào ở lại trên máy
| Dữ liệu | Ở đâu | Lý do |
|---|---|---|
| Email, mật khẩu | Supabase Auth (app không giữ mật khẩu) | Chỉ server băm và kiểm tra |
| Tên hiển thị, ảnh đại diện | Supabase (`profiles`, bucket avatars) | Cần để hiện tên trong chat |
| Thú cưng của tôi | Supabase, chỉ chủ xem được (RLS) | Dùng trên nhiều thiết bị |
| Tin nhắn chat | Supabase, chỉ thành viên nhóm đọc | Trao đổi giữa nhiều người |
| Kết quả nhận diện | Supabase, chỉ của chính mình | Phục vụ cải thiện model; có thể bỏ nếu muốn riêng tư hơn |
| Ảnh thú cưng người dùng chụp | Chỉ lên server khi đặt làm ảnh thú cưng hoặc bấm đóng góp | Không tự động gửi |
| Màu giao diện, token đăng nhập | Chỉ trên máy | Không cần chia sẻ |
| Loài, giống, wiki có sẵn, model nhận diện | Trong app | Dùng được khi không có mạng |

## 6. Kiểm tra nhanh sau khi cài
1. Đăng ký, đăng nhập. 2. Thêm thú cưng (có ảnh). 3. Chat: thấy tin chào mừng của nhóm. 4. Hai tài khoản cùng nuôi một loài: tin nhắn hiện gần như tức thì
(nếu chậm ~3 giây thì Realtime chưa nối được, app đang dùng polling). 5. Bộ sưu tập: cuộn xuống phải tải thêm ảnh. 6. Tắt mạng: wiki và nhận diện ảnh vẫn dùng được.
7. Đóng góp một ảnh, đăng nhập tài khoản ADMIN và duyệt, ảnh phải hiện trong bộ sưu tập.

## Giới hạn đã biết
- SQL Supabase và app CHƯA được chạy thử trên Supabase/Android thật.
- Đăng nhập bằng tên đăng nhập cần hàm `login_email` (ai biết username lấy được email). Muốn chặt hơn: `Config.ALLOW_USERNAME_LOGIN = false`.
- Ảnh cũ trên Storage không tự xóa khi xóa thú cưng/đổi ảnh. Bucket `contrib` công khai theo URL (không đoán được) nên ảnh đang chờ duyệt vẫn truy cập được nếu biết URL.
- Tin nhắn chỉ có chữ.
