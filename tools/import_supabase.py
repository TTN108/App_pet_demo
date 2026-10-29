#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Nhập thông tin ảnh bộ sưu tập (đã upload lên Supabase Storage bằng upload_storage.py) vào bảng gallery_images.

Dùng SERVICE ROLE KEY (quyền cao nhất, bỏ qua RLS) - chỉ để ở máy bạn qua biến môi trường, KHÔNG đưa vào app:
    set SUPABASE_URL=https://<ref>.supabase.co
    set SUPABASE_SERVICE_ROLE_KEY=eyJ...

Chạy:  python import_supabase.py --pet-dir pet --state storage_state.csv [--dry-run]
Upsert theo (source_type, external_id) nên chạy lại an toàn; ảnh nhập vào có status APPROVED ngay (nguồn API).
"""
import argparse
import csv
import json
import os
import sys
import time
from pathlib import Path

import requests

from breed_meta import BREED_ID, SPECIES_ID

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

BATCH = 200


def load_csv(path):
    with Path(path).open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def build_rows(manifest, state):
    by_ext = {r["external_id"]: r for r in state}
    rows, skipped = [], 0
    for m in manifest:
        st = by_ext.get(m["external_id"])
        key = (m["species"], m["breed_slug"])
        if not st or key not in BREED_ID:
            skipped += 1
            continue
        credit = " · ".join(x for x in [m.get("author"), m.get("license")] if x)
        rows.append({                                   # MỌI dòng phải có cùng bộ khóa (bulk insert của PostgREST)
            "pet_type_id": SPECIES_ID[m["species"]],
            "breed_id": BREED_ID[key],
            "source_type": "API",
            "external_id": m["external_id"],
            "status": "APPROVED",
            "image_path": st["image_path"],
            "thumb_path": st.get("thumb_path") or None,
            "width": int(st["width"]) if st.get("width") else None,
            "height": int(st["height"]) if st.get("height") else None,
            "title": m["breed_name"],
            "description": credit or None,
            "source_url": m.get("source_url") or None,
        })
    return rows, skipped


def post_batch(session, base, key, rows, retries=4):
    url = base.rstrip("/") + "/rest/v1/gallery_images"
    headers = {"apikey": key, "Authorization": "Bearer " + key, "Content-Type": "application/json",
               "Prefer": "resolution=merge-duplicates,return=minimal"}
    for attempt in range(retries):
        try:
            r = session.post(url, params={"on_conflict": "source_type,external_id"}, headers=headers,
                             data=json.dumps(rows), timeout=60)
            if r.status_code in (200, 201, 204):
                return True
            if r.status_code == 429 or r.status_code >= 500:
                time.sleep(3 * (attempt + 1))
                continue
            print("   lỗi %d: %s" % (r.status_code, r.text[:200]))
            return False
        except requests.RequestException as ex:
            print("   lỗi mạng (%s)" % ex)
            time.sleep(2 * (attempt + 1))
    return False


def main(argv=None):
    ap = argparse.ArgumentParser(description="Nhập gallery_images vào Supabase.")
    ap.add_argument("--pet-dir", default="pet")
    ap.add_argument("--state", default="storage_state.csv")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    manifest = load_csv(Path(args.pet_dir) / "manifest.csv")
    if not Path(args.state).exists():
        sys.exit("Không thấy %s. Chạy upload_storage.py trước." % args.state)
    rows, skipped = build_rows(manifest, load_csv(args.state))
    print("Sẽ nhập %d ảnh (bỏ qua %d ảnh chưa upload hoặc giống chưa có trong danh mục)" % (len(rows), skipped))
    if args.dry_run:
        print(json.dumps(rows[:2], ensure_ascii=False, indent=1))
        return 0
    base, key = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not base or not key:
        sys.exit("Thiếu SUPABASE_URL hoặc SUPABASE_SERVICE_ROLE_KEY.")
    session = requests.Session()
    sent = 0
    for i in range(0, len(rows), BATCH):
        chunk = rows[i:i + BATCH]
        if not post_batch(session, base, key, chunk):
            sys.exit("Lỗi khi nhập lô %d-%d; sửa rồi chạy lại (upsert nên không trùng)." % (i, i + len(chunk)))
        sent += len(chunk)
        print("  ... %d/%d" % (sent, len(rows)))
    print("Xong.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
