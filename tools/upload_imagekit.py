#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Đẩy ảnh trong pet/ (do download_pet_images.py tạo) lên ImageKit, ghi kết quả vào imagekit_state.csv.

KHÓA RIÊNG của ImageKit chỉ dùng ở máy bạn qua biến môi trường - TUYỆT ĐỐI không đưa vào app hay commit lên git:
    set IMAGEKIT_PRIVATE_KEY=private_xxx          (Windows)   /   export IMAGEKIT_PRIVATE_KEY=private_xxx

Chạy:
    python upload_imagekit.py --pet-dir pet                 # tải lên tất cả ảnh chưa có trong state
    python upload_imagekit.py --pet-dir pet --dry-run       # chỉ liệt kê, không gọi mạng
Chạy lại bao nhiêu lần cũng được: ảnh đã có trong state bị bỏ qua; mất state thì tải lại sẽ ghi đè đúng tên cũ
(useUniqueFileName=false) nên không sinh ảnh trùng.
Bước tiếp theo: python import_supabase.py
"""
import argparse
import csv
import os
import sys
import time
from datetime import datetime
from pathlib import Path

import requests

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

UPLOAD_URL = os.environ.get("IMAGEKIT_UPLOAD_URL", "https://upload.imagekit.io/api/v1/files/upload")
STATE_FIELDS = ["external_id", "file", "image_path", "width", "height", "file_id", "uploaded_at"]


def load_csv(path):
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def upload_one(session, private_key, local_path, folder, tags, retries=4):
    """Trả về dict kết quả của ImageKit hoặc None. 4xx (trừ 429) không thử lại vì thử lại cũng vô ích."""
    for attempt in range(retries):
        try:
            with open(local_path, "rb") as fh:
                resp = session.post(
                    UPLOAD_URL, auth=(private_key, ""), timeout=120,
                    files={"file": (local_path.name, fh, "image/jpeg")},
                    data={"fileName": local_path.name, "folder": folder, "useUniqueFileName": "false",
                          "overwriteFile": "true", "tags": tags})
            if resp.status_code == 429 or resp.status_code >= 500:
                time.sleep(3 * (attempt + 1))
                continue
            if resp.status_code in (401, 403):
                raise PermissionError("ImageKit từ chối khóa (HTTP %d). Kiểm tra IMAGEKIT_PRIVATE_KEY." % resp.status_code)
            if resp.status_code >= 400:
                print("   lỗi %d: %s" % (resp.status_code, resp.text[:150]))
                return None
            return resp.json()
        except (requests.RequestException, ValueError) as ex:
            print("   lỗi mạng (%s), thử lại" % ex)
            time.sleep(2 * (attempt + 1))
    return None


def main(argv=None):
    ap = argparse.ArgumentParser(description="Tải ảnh lên ImageKit.")
    ap.add_argument("--pet-dir", default="pet")
    ap.add_argument("--root-folder", default="/pets", help="thư mục gốc trên ImageKit")
    ap.add_argument("--state", default="imagekit_state.csv")
    ap.add_argument("--delay", type=float, default=0.15)
    ap.add_argument("--limit", type=int, default=0, help="chỉ tải tối đa N ảnh (0 = tất cả)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    pet_dir = Path(args.pet_dir)
    manifest = load_csv(pet_dir / "manifest.csv")
    if not manifest:
        sys.exit("Không thấy %s. Chạy download_pet_images.py trước." % (pet_dir / "manifest.csv"))
    state_path = Path(args.state)
    done = {r["external_id"] for r in load_csv(state_path)}
    todo = [r for r in manifest if r["external_id"] not in done and (pet_dir / r["file"]).exists()]
    if args.limit:
        todo = todo[:args.limit]
    print("Manifest %d ảnh | đã tải %d | cần tải %d" % (len(manifest), len(done), len(todo)))
    if args.dry_run:
        for r in todo[:10]:
            print("  sẽ tải:", r["file"], "->", "%s/%s/%s" % (args.root_folder, r["species"], r["breed_slug"]))
        return 0

    key = os.environ.get("IMAGEKIT_PRIVATE_KEY")
    if not key:
        sys.exit("Thiếu biến môi trường IMAGEKIT_PRIVATE_KEY.")
    session = requests.Session()
    new_file = not state_path.exists()
    ok = fail = consecutive_fail = 0
    with state_path.open("a", newline="", encoding="utf-8") as sf:
        writer = csv.DictWriter(sf, fieldnames=STATE_FIELDS)
        if new_file:
            writer.writeheader()
        for r in todo:
            local = pet_dir / r["file"]
            folder = "%s/%s/%s" % (args.root_folder.rstrip("/"), r["species"], r["breed_slug"])
            result = upload_one(session, key, local, folder, "%s,%s" % (r["species"], r["breed_slug"]))
            if not result or "filePath" not in result:
                fail += 1
                consecutive_fail += 1
                if consecutive_fail >= 5:
                    sys.exit("5 lần lỗi liên tiếp, dừng lại. Đã tải %d ảnh; chạy lại để tiếp tục." % ok)
                continue
            consecutive_fail = 0
            writer.writerow({"external_id": r["external_id"], "file": r["file"], "image_path": result["filePath"],
                             "width": result.get("width", ""), "height": result.get("height", ""),
                             "file_id": result.get("fileId", ""), "uploaded_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")})
            sf.flush()
            ok += 1
            if ok % 50 == 0:
                print("  ... %d ảnh" % ok)
            time.sleep(args.delay)
    print("Xong: +%d ảnh, lỗi %d. State: %s" % (ok, fail, state_path))
    return 0


if __name__ == "__main__":
    sys.exit(main())
