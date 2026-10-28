# App Pet v4 - Supabase + ImageKit

## Kiến trúc
| Dữ liệu | Nơi lưu |
|---|---|
| Tài khoản, đăng nhập | Supabase Auth |
| Hồ sơ, thú cưng, ảnh đại diện, chat, báo cáo, bộ sưu tập (thông tin ảnh) | Supabase Postgres (+ RLS) |
| Ảnh đại diện (người dùng tải lên) | Supabase Storage, bucket `avatars` |
| Ảnh bộ sưu tập (hàng nghìn ảnh) | ImageKit (app yêu cầu ảnh đã thu nhỏ qua tham số `tr=`) |
| Loài, giống, alias nhận diện, wiki chăm sóc | Trong app (SQLite, dùng được khi không có mạng) |
| Model nhận diện giống | Trong app (TFLite, chạy trên máy) |

ID loài/giống GIỐNG HỆT giữa app và Supabase (xem `tools/breed_meta.py`). Muốn thêm loài/giống chỉ thêm vào CUỐI danh sách.

## 1. Supabase
1. Tạo project. SQL Editor chạy lần lượt: `supabase/001_schema.sql` -> `002_security.sql` -> `003_seed_catalog.sql` -> `004_contributions.sql`.
2. Authentication > Providers > Email: khi demo có thể tắt "Confirm email" (email xác nhận mặc định bị giới hạn số lượng gửi rất thấp).
3. Project Settings > API: lấy `Project URL` và `anon public key`.
4. Muốn có tài khoản quản trị: `update public.profiles set role = 'ADMIN' where username = 'ten_cua_ban';`
5. KHÔNG bao giờ đưa `service_role key` vào app.

## 2. ImageKit
Tạo tài khoản, lấy `URL endpoint` (dạng `https://ik.imagekit.io/<id>`) và `Private key` (chỉ để ở máy bạn).

## 3. Cấu hình app
Sao chép `local.properties.example` thành `local.properties` rồi điền `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `IMAGEKIT_ENDPOINT`. Build lại app.

## 4. Dữ liệu (thư mục `tools/`)
```bash
pip install requests pillow beautifulsoup4 lxml
python download_pet_images.py --per-breed 40 --out pet               # thu thập ảnh + manifest.csv
set IMAGEKIT_PRIVATE_KEY=private_xxx
python upload_imagekit.py --pet-dir pet                              # đẩy ảnh lên ImageKit -> imagekit_state.csv
set SUPABASE_URL=https://xxx.supabase.co
set SUPABASE_SERVICE_ROLE_KEY=eyJ...                                 # chỉ trên máy bạn
python import_supabase.py --pet-dir pet --state imagekit_state.csv   # ghi gallery_images vào Supabase
python crawl_care_wiki.py --source wikipedia --out wiki_out          # bài wiki
python build_catalog.py --wiki wiki_out/wiki_articles.jsonl --check  # catalog trong app + seed Supabase
python train_breed_classifier.py --pet-dir pet                       # model nhận diện (cần TensorFlow; có thể chạy trên Kaggle)
```
Sau `build_catalog.py`, chạy lại `supabase/003_seed_catalog.sql` nếu có đổi loài/giống, rồi build lại app.

## 5. Kiểm tra nhanh sau khi cài
1. Đăng ký tài khoản, đăng nhập. 2. Thêm thú cưng (có ảnh). 3. Mở Chat: thấy tin chào mừng của nhóm. 4. Dùng 2 máy/2 tài khoản
cùng nuôi một loài: nhắn tin phải hiện gần như tức thì (nếu chậm ~3 giây thì Realtime chưa nối được, app đang dùng polling).
5. Bộ sưu tập: cuộn xuống phải tải thêm ảnh. 6. Tắt mạng: wiki và nhận diện ảnh vẫn dùng được.

## Giới hạn đã biết
- SQL Supabase và app CHƯA được chạy thử trên Supabase/Android thật (xem ghi chú trong câu trả lời).
- Đăng nhập bằng tên đăng nhập cần hàm `login_email` (ai biết username lấy được email). Muốn chặt hơn: `Config.ALLOW_USERNAME_LOGIN = false`.
- Ảnh đại diện cũ trên Storage không tự xóa khi xóa thú cưng/đổi ảnh.
- Tin nhắn chỉ có chữ (cột `image_url` đã sẵn sàng nhưng chưa có giao diện gửi ảnh).


## Đóng góp của người dùng (v5)
| Luồng | Cách hoạt động |
|---|---|
| Wiki từ chat | Nhấn giữ một tin nhắn trong chat > "Đề xuất đưa vào wiki": người dùng sửa tiêu đề/nội dung, chọn danh mục, quyết định có hiện tên mình hay không. Bài vào trạng thái PENDING (bảng `community_wiki`), ADMIN duyệt thì hiện trong mục Wiki cho mọi người (cùng với bài có sẵn trong app). Không có chuyện app tự cào toàn bộ chat. |
| Ảnh người dùng tải lên | Màn Bộ sưu tập > nút "+ Ảnh" (phải chọn loài và giống ở bộ lọc trước) > đồng ý điều khoản > chọn ảnh. |
| Ảnh từ lần chụp camera | Màn Chụp ảnh nhận diện > sau khi chọn đúng giống > "Đóng góp ảnh này cho bộ sưu tập". Server ghi lại việc người dùng có ĐỔI giống so với dự đoán của model hay không (`label_changed`) = ví dụ khó. |
| Gửi ảnh | WorkManager xếp hàng, tự gửi khi có mạng và thử lại nếu lỗi tạm thời. Ảnh được thu nhỏ, xoay đúng chiều và xóa EXIF (kể cả vị trí) trước khi gửi. |
| Duyệt | Tài khoản ADMIN thấy nút "Quản trị nội dung" trong Hồ sơ: duyệt/từ chối ảnh, bài wiki, xử lý báo cáo chat. Đặt quyền: `update public.profiles set role = 'ADMIN' where username = '...';` |
| Huấn luyện lại | `python export_training_set.py --out train_data` rồi `python train_breed_classifier.py --pet-dir train_data` (xem `tools/`). |

## Dữ liệu nào lên Supabase, dữ liệu nào ở lại trên máy
| Dữ liệu | Ở đâu | Lý do |
|---|---|---|
| Email, mật khẩu | Supabase Auth (app không giữ mật khẩu) | Chỉ server băm và kiểm tra |
| Tên hiển thị, ảnh đại diện | Supabase (`profiles`, bucket avatars) | Cần để hiện tên trong chat; ảnh đại diện công khai |
| Thú cưng của tôi | Supabase, chỉ chủ xem được (RLS) | Dùng trên nhiều thiết bị |
| Tin nhắn chat | Supabase, chỉ thành viên nhóm đọc | Trao đổi giữa nhiều người |
| Kết quả nhận diện | Supabase, chỉ của chính mình | Phục vụ cải thiện model; có thể bỏ nếu muốn riêng tư hơn |
| Ảnh thú cưng người dùng chụp | Chỉ lên server khi người dùng đặt làm ảnh thú cưng hoặc bấm đóng góp | Không tự động gửi |
| Màu giao diện, token đăng nhập | Chỉ trên máy | Không cần chia sẻ |
| Loài, giống, wiki có sẵn, model nhận diện | Trong app | Dùng được khi không có mạng |

## Chức năng nên làm thêm (gợi ý, chưa có trong code)
Dựa trên các ứng dụng chăm sóc thú cưng phổ biến, nhóm chức năng hay gặp: hồ sơ sức khỏe và lịch tiêm phòng/tẩy giun kèm nhắc lịch, theo dõi cân nặng, nhật ký chăm sóc hằng ngày, tìm phòng khám thú y gần đây, bảng tin tìm thú lạc, hỏi đáp có chuyên gia. Ưu tiên cho đồ án: (1) nhắc lịch tiêm phòng bằng WorkManager, (2) biểu đồ cân nặng, (3) tìm thú lạc theo cộng đồng loài.
