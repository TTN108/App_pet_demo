# Công cụ dữ liệu cho App Pet

Xem hướng dẫn đầy đủ ở `../SETUP.md` (mục 3). Tóm tắt:

| Script | Việc | Cần |
|---|---|---|
| `download_pet_images.py` | Thu thập ảnh + `manifest.csv` | requests, pillow |
| `upload_storage.py` | Tạo ảnh đầy đủ + ảnh nhỏ, tải lên bucket `gallery` của Supabase (`--dry-run` để ước lượng dung lượng) | requests, pillow |
| `import_supabase.py` | Ghi `gallery_images` vào Supabase | requests |
| `crawl_care_wiki.py` | Bài wiki từ Wikipedia (và diễn đàn nếu cấu hình) | requests, bs4, lxml |
| `build_catalog.py` | Catalog trong app (SQLite) + `supabase/003_seed_catalog.sql` | - |
| `export_training_set.py` | Xuất ảnh ĐÃ DUYỆT từ Supabase (kèm ví dụ khó, chia train/val/test cố định) | requests, pillow |
| `train_breed_classifier.py` | Model nhận diện giống (TFLite) | tensorflow |

Các khóa bí mật (service_role) chỉ đặt trong biến môi trường trên máy bạn, không đưa vào app hay git.
