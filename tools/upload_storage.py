#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Tạo ảnh đầy đủ (cạnh dài <= 1024px) + ảnh nhỏ (cạnh dài <= 360px) từ pet/ rồi tải lên bucket public "gallery" của
Supabase Storage. Gói free của Supabase KHÔNG tự thu nhỏ ảnh, nên ảnh nhỏ phải tạo sẵn ở bước này.

Dùng SERVICE ROLE KEY (chỉ ở máy bạn, không đưa vào app/git):
    set SUPABASE_URL=https://xxx.supabase.co
    set SUPABASE_SERVICE_ROLE_KEY=eyJ...
Chạy:
    python upload_storage.py --pet-dir pet --dry-run     # ước lượng dung lượng, không tải lên
    python upload_storage.py --pet-dir pet               # tải lên, ghi storage_state.csv
    python import_supabase.py --pet-dir pet              # rồi ghi thông tin ảnh vào bảng gallery_images
Chạy lại thoải mái: ảnh đã có trong state bị bỏ qua (x-upsert nên tải lại cũng không sinh bản trùng).
Có chốt chặn --budget-mb (mặc định 900) để không vượt giới hạn 1 GB lưu file của gói free.
"""
import argparse
import csv
import io
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import requests
from PIL import Image, ImageOps

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

BUCKET = "gallery"
STATE_FIELDS = ["external_id", "file", "image_path", "thumb_path", "width", "height", "bytes_full", "bytes_thumb", "uploaded_at"]


def load_csv(path):
    if not Path(path).exists():
        return []
    with Path(path).open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def render(path, max_side, quality):
    """Mở ảnh, xoay đúng chiều theo EXIF, thu nhỏ giữ tỉ lệ, trả về (bytes JPEG, width, height). EXIF/vị trí bị loại."""
    img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    img.thumbnail((max_side, max_side), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=True)
    return buf.getvalue(), img.width, img.height


def upload(session, base, key, object_path, data, retries=4):
    url = "%s/storage/v1/object/%s/%s" % (base.rstrip("/"), BUCKET, object_path)
    headers = {"apikey": key, "Authorization": "Bearer " + key, "Content-Type": "image/jpeg", "x-upsert": "true"}
    for attempt in range(retries):
        try:
            r = session.post(url, headers=headers, data=data, timeout=120)
            if r.status_code in (200, 201):
                return True
            if r.status_code == 429 or r.status_code >= 500:
                time.sleep(3 * (attempt + 1))
                continue
            if r.status_code in (401, 403):
                raise PermissionError("Supabase từ chối khóa (HTTP %d). Kiểm tra SUPABASE_SERVICE_ROLE_KEY." % r.status_code)
            print("   lỗi %d: %s" % (r.status_code, r.text[:150]))
            return False
        except requests.RequestException as ex:
            print("   lỗi mạng (%s), thử lại" % ex)
            time.sleep(2 * (attempt + 1))
    return False


def main(argv=None):
    ap = argparse.ArgumentParser(description="Tải ảnh bộ sưu tập (đầy đủ + ảnh nhỏ) lên Supabase Storage.")
    ap.add_argument("--pet-dir", default="pet")
    ap.add_argument("--state", default="storage_state.csv")
    ap.add_argument("--full-size", type=int, default=1024, help="cạnh dài tối đa của ảnh đầy đủ")
    ap.add_argument("--thumb-size", type=int, default=360, help="cạnh dài tối đa của ảnh nhỏ")
    ap.add_argument("--quality", type=int, default=80, help="chất lượng JPEG ảnh đầy đủ (ảnh nhỏ = quality - 8)")
    ap.add_argument("--budget-mb", type=float, default=900.0, help="dừng khi tổng dung lượng vượt mức này (gói free: 1 GB)")
    ap.add_argument("--delay", type=float, default=0.1)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    pet_dir = Path(args.pet_dir)
    manifest = load_csv(pet_dir / "manifest.csv")
    if not manifest:
        sys.exit("Không thấy %s. Chạy download_pet_images.py trước." % (pet_dir / "manifest.csv"))
    state = load_csv(args.state)
    done = {r["external_id"] for r in state}
    used = sum(int(r.get("bytes_full") or 0) + int(r.get("bytes_thumb") or 0) for r in state)
    todo = [r for r in manifest if r["external_id"] not in done and (pet_dir / r["file"]).exists()]
    if args.limit:
        todo = todo[:args.limit]
    budget = int(args.budget_mb * 1024 * 1024)
    print("Manifest %d ảnh | đã tải %d (%.1f MB) | cần tải %d" % (len(manifest), len(done), used / 1e6, len(todo)))

    key, base = os.environ.get("SUPABASE_SERVICE_ROLE_KEY"), os.environ.get("SUPABASE_URL")
    if not args.dry_run and (not key or not base):
        sys.exit("Thiếu SUPABASE_URL hoặc SUPABASE_SERVICE_ROLE_KEY.")
    session = requests.Session()
    new_file = not Path(args.state).exists()
    ok = fail = consecutive_fail = 0
    est_total = 0
    sf = None if args.dry_run else open(args.state, "a", newline="", encoding="utf-8")
    writer = None
    if sf:
        writer = csv.DictWriter(sf, fieldnames=STATE_FIELDS)
        if new_file:
            writer.writeheader()
    try:
        for r in todo:
            local = pet_dir / r["file"]
            try:
                full, w, h = render(local, args.full_size, args.quality)
                thumb, _tw, _th = render(local, args.thumb_size, max(args.quality - 8, 40))
            except (OSError, ValueError) as ex:
                print("   bỏ qua ảnh hỏng %s: %s" % (r["file"], ex))
                fail += 1
                continue
            size = len(full) + len(thumb)
            if used + est_total + size > budget:
                print("DỪNG: sẽ vượt ngân sách %.0f MB (đã dùng %.1f MB). Giảm --full-size/--quality hoặc nâng ngân sách."
                      % (args.budget_mb, (used + est_total) / 1e6))
                break
            est_total += size
            if args.dry_run:
                continue
            stem = Path(r["file"]).with_suffix("").as_posix()
            image_path, thumb_path = stem + ".jpg", stem + "_t.jpg"
            if not (upload(session, base, key, image_path, full) and upload(session, base, key, thumb_path, thumb)):
                fail += 1
                consecutive_fail += 1
                est_total -= size
                if consecutive_fail >= 5:
                    sys.exit("5 lần lỗi liên tiếp, dừng lại. Đã tải %d ảnh; chạy lại để tiếp tục." % ok)
                continue
            consecutive_fail = 0
            writer.writerow({"external_id": r["external_id"], "file": r["file"], "image_path": image_path,
                             "thumb_path": thumb_path, "width": w, "height": h, "bytes_full": len(full),
                             "bytes_thumb": len(thumb), "uploaded_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")})
            sf.flush()
            ok += 1
            if ok % 50 == 0:
                print("  ... %d ảnh" % ok)
            time.sleep(args.delay)
    finally:
        if sf:
            sf.close()
    if args.dry_run:
        n = max(len(todo) - fail, 1)
        print("ƯỚC LƯỢNG: %d ảnh cần %.1f MB (trung bình %.0f KB/ảnh gồm cả ảnh nhỏ). Tổng sau khi tải: %.1f MB / giới hạn gói free 1024 MB."
              % (len(todo), est_total / 1e6, est_total / n / 1e3, (used + est_total) / 1e6))
    else:
        print("Xong: +%d ảnh, lỗi %d. Tổng dung lượng đã dùng khoảng %.1f MB. State: %s" % (ok, fail, (used + est_total) / 1e6, args.state))
    return 0


if __name__ == "__main__":
    sys.exit(main())
