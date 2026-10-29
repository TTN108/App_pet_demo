#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Xuất bộ dữ liệu huấn luyện từ bộ sưu tập ĐÃ DUYỆT trên Supabase (ảnh cào + ảnh người dùng đóng góp từ upload/camera).

Dùng SERVICE ROLE KEY (chỉ ở máy bạn qua biến môi trường). Ảnh chỉ lấy khi status = APPROVED.
    set SUPABASE_URL=https://xxx.supabase.co
    set SUPABASE_SERVICE_ROLE_KEY=eyJ...
    python export_training_set.py --out train_data
    python train_breed_classifier.py --pet-dir train_data

Kết quả trong --out:
  <breed_slug>/<id>.jpg      ảnh đã kiểm tra hợp lệ, lưu lại JPEG
  manifest.csv               file, breed_slug, species, gallery_id, origin, label_changed, split, source_type
  hard_examples.csv          ảnh từ camera mà người dùng ĐÃ ĐỔI giống so với dự đoán của model (nên xem lại bằng mắt)
Chia tập CỐ ĐỊNH theo id ảnh (băm): 10% test, 10% val, 80% train. Thêm ảnh mới không làm ảnh cũ đổi tập,
nên so sánh model cũ/mới trên cùng tập test là công bằng.
"""
import argparse
import csv
import hashlib
import io
import os
import sys
from collections import Counter
from pathlib import Path

import requests
from PIL import Image

from breed_meta import BREED_ID

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

PAGE = 1000
SLUG_BY_BREED = {bid: slug for (_sp, slug), bid in BREED_ID.items()}
SPECIES_BY_BREED = {bid: sp for (sp, _slug), bid in BREED_ID.items()}


def split_of(gallery_id):
    """Ổn định theo id: 0 -> test, 1 -> val, còn lại -> train."""
    h = int(hashlib.sha1(str(gallery_id).encode()).hexdigest(), 16) % 10
    return "test" if h == 0 else "val" if h == 1 else "train"


def fetch_rows(session, base, key):
    after = 0
    while True:
        r = session.get(base.rstrip("/") + "/rest/v1/gallery_images", timeout=60, headers={
            "apikey": key, "Authorization": "Bearer " + key}, params={
            "select": "id,breed_id,image_path,origin,label_changed,source_type", "status": "eq.APPROVED",
            "breed_id": "not.is.null", "id": "gt.%d" % after, "order": "id.asc", "limit": str(PAGE)})
        r.raise_for_status()
        rows = r.json()
        if not rows:
            return
        yield from rows
        after = rows[-1]["id"]


def image_url(path, base):
    """URL tuyệt đối (ảnh người dùng ở bucket contrib) giữ nguyên; đường dẫn tương đối nằm trong bucket public gallery."""
    if path.startswith("http://") or path.startswith("https://"):
        return path
    return base.rstrip("/") + "/storage/v1/object/public/gallery/" + path.lstrip("/")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Xuất dữ liệu huấn luyện từ gallery đã duyệt.")
    ap.add_argument("--out", default="train_data")
    ap.add_argument("--min-side", type=int, default=96, help="bỏ ảnh có cạnh ngắn hơn mức này")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args(argv)

    base, key = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not base or not key:
        sys.exit("Thiếu SUPABASE_URL hoặc SUPABASE_SERVICE_ROLE_KEY.")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    manifest, hard, skipped = [], [], Counter()
    for row in fetch_rows(session, base, key):
        if args.limit and len(manifest) >= args.limit:
            break
        slug = SLUG_BY_BREED.get(row["breed_id"])
        if not slug:
            skipped["giống không có trong danh mục"] += 1
            continue
        rel = "%s/%d.jpg" % (slug, row["id"])
        dest = out / rel
        if not dest.exists():
            try:
                resp = session.get(image_url(row["image_path"], base), timeout=60)
                resp.raise_for_status()
                img = Image.open(io.BytesIO(resp.content))
                img.load()
                if min(img.size) < args.min_side:
                    skipped["ảnh quá nhỏ"] += 1
                    continue
                dest.parent.mkdir(parents=True, exist_ok=True)
                img.convert("RGB").save(dest, "JPEG", quality=90)
            except (requests.RequestException, OSError, ValueError) as ex:
                skipped["lỗi tải/đọc ảnh"] += 1
                print("  bỏ qua id %s: %s" % (row["id"], ex))
                continue
        item = {"file": rel, "breed_slug": slug, "species": SPECIES_BY_BREED[row["breed_id"]],
                "gallery_id": row["id"], "origin": row.get("origin") or "", "split": split_of(row["id"]),
                "label_changed": "" if row.get("label_changed") is None else str(bool(row["label_changed"])).lower(),
                "source_type": row.get("source_type") or ""}
        manifest.append(item)
        if row.get("label_changed"):
            hard.append(item)

    fields = ["file", "breed_slug", "species", "gallery_id", "origin", "label_changed", "split", "source_type"]
    for name, rows in (("manifest.csv", manifest), ("hard_examples.csv", hard)):
        with (out / name).open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(rows)
    per = Counter(m["breed_slug"] for m in manifest)
    print("Xuất %d ảnh, %d ví dụ khó (người dùng đổi giống). Bỏ qua: %s" % (len(manifest), len(hard), dict(skipped) or "không"))
    for slug, n in sorted(per.items()):
        print("  %-20s %4d%s" % (slug, n, "   <-- ít ảnh, nên bổ sung" if n < 15 else ""))
    print("Số ảnh theo tập:", dict(Counter(m["split"] for m in manifest)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
