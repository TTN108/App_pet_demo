#!/usr/bin/env python3
"""
Thu thập ảnh thú cưng theo cấu trúc:  pet/<loài>/<giống>/<giống>_0001.jpg

Nguồn dữ liệu (đều là API công khai, không cào HTML):
  - dogceo     : https://dog.ceo/dog-api/            (ảnh chó theo giống, không cần key)
  - catapi     : https://thecatapi.com/              (ảnh mèo theo giống; key miễn phí là tùy chọn)
  - wikimedia  : https://commons.wikimedia.org/       (mọi loài; chỉ lấy ảnh CC BY / CC BY-SA / CC0 / public domain)

Cài đặt:
    pip install requests pillow

Chạy:
    python download_pet_images.py                       # mặc định 40 ảnh/giống, lưu vào ./pet
    python download_pet_images.py --per-breed 60 --out pet
    python download_pet_images.py --species dog cat     # chỉ chạy một số loài
    python download_pet_images.py --list                # xem danh sách loài/giống

Chạy lại bao nhiêu lần cũng được: ảnh đã tải (theo manifest.csv) sẽ được bỏ qua.

Tùy chọn (khuyến nghị nếu dùng nhiều):
    set CAT_API_KEY=xxxx        (Windows)   /   export CAT_API_KEY=xxxx   (Linux, macOS)
    -> lấy key miễn phí ở https://thecatapi.com để tải được nhiều ảnh mèo hơn.

QUAN TRỌNG: đổi USER_AGENT bên dưới thành email của bạn (Wikimedia yêu cầu).
"""
import argparse
import csv
import hashlib
import html
import io
import os
import random
import re
import sys
import time
from pathlib import Path

import requests

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

try:
    from PIL import Image
except ImportError:  # Không có Pillow vẫn chạy, chỉ là không kiểm tra/thu nhỏ ảnh.
    Image = None

USER_AGENT = "AppPetDemoImageCollector/1.0 (student project; contact: your-email@example.com)"
MIN_SIDE = 200          # bỏ ảnh quá nhỏ (cần Pillow)
MAX_SIDE = 1024         # thu nhỏ ảnh lớn để app/DB nhẹ (cần Pillow)
MAX_WIKI_REQUESTS = 20  # giới hạn số lần gọi API Wikimedia cho mỗi giống

# --------------------------------------------------------------------------
# Danh mục: khớp với dữ liệu mẫu trong DatabaseHelper (pet_types / breeds).
# sources: danh sách (tên nguồn, tham số) - thử lần lượt cho tới khi đủ số ảnh.
# --------------------------------------------------------------------------
CATALOG = {
    "dog": {
        "type_vi": "Chó",
        "breeds": {
            "poodle": {"name": "Poodle", "sources": [("dogceo", "poodle"), ("wikimedia", "Poodle dog")]},
            "golden-retriever": {"name": "Golden Retriever",
                                 "sources": [("dogceo", "retriever/golden"), ("wikimedia", "Golden Retriever dog")]},
            "corgi": {"name": "Corgi",
                      "sources": [("dogceo", "corgi/cardigan"), ("wikimedia", "Pembroke Welsh Corgi")]},
            "pug": {"name": "Pug", "sources": [("dogceo", "pug"), ("wikimedia", "Pug dog")]},
        },
    },
    "cat": {
        "type_vi": "Mèo",
        "breeds": {
            "british-shorthair": {"name": "British Shorthair",
                                  "sources": [("catapi", "bsho"), ("wikimedia", "British Shorthair cat")]},
            "persian": {"name": "Persian", "sources": [("catapi", "pers"), ("wikimedia", "Persian cat")]},
            "siamese": {"name": "Siamese", "sources": [("catapi", "siam"), ("wikimedia", "Siamese cat")]},
        },
    },
    "bird": {
        "type_vi": "Chim",
        "breeds": {
            "budgerigar": {"name": "Budgerigar", "sources": [("wikimedia", "Budgerigar")]},
            "cockatiel": {"name": "Cockatiel", "sources": [("wikimedia", "Cockatiel")]},
        },
    },
    "fish": {
        "type_vi": "Cá",
        "breeds": {
            "betta": {"name": "Betta", "sources": [("wikimedia", "Betta splendens")]},
            "goldfish": {"name": "Goldfish", "sources": [("wikimedia", "Goldfish")]},
        },
    },
    "rabbit": {
        "type_vi": "Thỏ",
        "breeds": {
            "netherland-dwarf": {"name": "Netherland Dwarf", "sources": [("wikimedia", "Netherland Dwarf rabbit")]},
            "lionhead": {"name": "Lionhead", "sources": [("wikimedia", "Lionhead rabbit")]},
        },
    },
}

MANIFEST_FIELDS = [
    "pet_type", "species", "breed_name", "breed_slug", "file", "source_type", "external_id",
    "image_url", "source_url", "license", "author", "sha256", "width", "height",
]

BAD_TITLE = re.compile(r"stamp|coin|drawing|illustration|poster|map|logo|diagram|painting|"
                       r"skeleton|skull|cartoon|toy|plush|statue|\.svg", re.I)


# --------------------------------------------------------------------------
# Tiện ích mạng
# --------------------------------------------------------------------------
def make_session():
    s = requests.Session()
    s.headers.update({"User-Agent": USER_AGENT})
    return s


def get_json(session, url, params=None, headers=None, retries=3):
    for attempt in range(retries):
        try:
            r = session.get(url, params=params, headers=headers, timeout=30)
            if r.status_code == 404:
                return None
            if r.status_code == 429:
                time.sleep(5 * (attempt + 1))
                continue
            r.raise_for_status()
            return r.json()
        except (requests.RequestException, ValueError):
            time.sleep(2 * (attempt + 1))
    return None


def download(session, url, retries=3):
    for attempt in range(retries):
        try:
            r = session.get(url, timeout=60)
            if r.status_code == 429:
                time.sleep(5 * (attempt + 1))
                continue
            r.raise_for_status()
            if not r.headers.get("Content-Type", "").startswith("image/"):
                return None
            return r.content
        except requests.RequestException:
            time.sleep(2 * (attempt + 1))
    return None


# --------------------------------------------------------------------------
# Các nguồn: mỗi hàm là generator trả về dict ứng viên
# --------------------------------------------------------------------------
def source_dogceo(session, path):
    data = get_json(session, "https://dog.ceo/api/breed/%s/images" % path)
    if not data or data.get("status") != "success":
        return
    urls = list(data["message"])
    random.Random(42).shuffle(urls)  # trộn để poodle không toàn một giống phụ
    for url in urls:
        yield {
            "image_url": url,
            "source_url": "https://dog.ceo/dog-api/",
            "external_id": "dogceo:" + url.rsplit("/", 1)[-1],
            "license": "Dog CEO API (Stanford Dogs Dataset)",
            "author": "",
        }


def source_catapi(session, breed_id):
    key = os.environ.get("CAT_API_KEY")
    headers = {"x-api-key": key} if key else {}
    limit = 100 if key else 10
    seen = set()
    for page in range(30):
        data = get_json(session, "https://api.thecatapi.com/v1/images/search",
                        params={"breed_ids": breed_id, "limit": limit, "page": page, "order": "ASC"},
                        headers=headers)
        if not data:
            return
        new_items = [i for i in data if i.get("id") not in seen]
        if not new_items:
            return
        for item in new_items:
            seen.add(item["id"])
            yield {
                "image_url": item["url"],
                "source_url": "https://thecatapi.com/",
                "external_id": "catapi:" + item["id"],
                "license": "TheCatAPI",
                "author": "",
            }
        if len(data) < limit:
            return


def license_ok(name):
    if not name:
        return False
    if re.search(r"-(NC|ND)\b", name, re.I):
        return False
    return bool(re.match(r"^(CC[ -]BY|CC0|Public domain|PD)", name, re.I))


def strip_html(text):
    return html.unescape(re.sub(r"<[^>]+>", "", text or "")).strip()


def source_wikimedia(session, query):
    params = {
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": "filetype:bitmap " + query, "gsrnamespace": 6, "gsrlimit": 50,
        "prop": "imageinfo", "iiprop": "url|mime|size|extmetadata", "iiurlwidth": MAX_SIDE,
    }
    cont = {}
    for _ in range(MAX_WIKI_REQUESTS):
        data = get_json(session, "https://commons.wikimedia.org/w/api.php", params=dict(params, **cont))
        if not data:
            return
        pages = (data.get("query") or {}).get("pages") or {}
        for page in sorted(pages.values(), key=lambda p: p.get("index", 0)):
            info = (page.get("imageinfo") or [None])[0]
            if not info or info.get("mime") not in ("image/jpeg", "image/png"):
                continue
            if BAD_TITLE.search(page.get("title", "")):
                continue
            meta = info.get("extmetadata") or {}
            license_name = (meta.get("LicenseShortName") or {}).get("value", "")
            if not license_ok(license_name):
                continue
            yield {
                "image_url": info.get("thumburl") or info["url"],
                "source_url": info.get("descriptionurl", ""),
                "external_id": "wikimedia:%s" % page.get("pageid"),
                "license": license_name,
                "author": strip_html((meta.get("Artist") or {}).get("value", "")),
            }
        if "continue" not in data:
            return
        cont = data["continue"]


SOURCES = {"dogceo": source_dogceo, "catapi": source_catapi, "wikimedia": source_wikimedia}


# --------------------------------------------------------------------------
# Xử lý ảnh + manifest
# --------------------------------------------------------------------------
def process_image(data):
    """Trả về (bytes, ext, width, height) hoặc None nếu ảnh hỏng/quá nhỏ."""
    if Image is None:
        return data, ".jpg", 0, 0
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except Exception:
        return None
    if min(img.size) < MIN_SIDE:
        return None
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    out = io.BytesIO()
    img.convert("RGB").save(out, format="JPEG", quality=90)
    return out.getvalue(), ".jpg", img.size[0], img.size[1]


def load_manifest(path):
    rows = []
    if path.exists():
        with path.open(newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
    return rows


def next_index(folder, slug):
    best = 0
    for p in folder.glob(slug + "_*"):
        m = re.match(re.escape(slug) + r"_(\d+)", p.stem)
        if m:
            best = max(best, int(m.group(1)))
    return best + 1


def main():
    ap = argparse.ArgumentParser(description="Thu thập ảnh thú cưng theo loài/giống.")
    ap.add_argument("--out", default="pet", help="thư mục gốc (mặc định: pet)")
    ap.add_argument("--per-breed", type=int, default=40, help="số ảnh mỗi giống (mặc định: 40)")
    ap.add_argument("--species", nargs="*", help="chỉ chạy các loài này: dog cat bird fish rabbit")
    ap.add_argument("--delay", type=float, default=0.4, help="giây nghỉ giữa các lần tải (mặc định 0.4)")
    ap.add_argument("--list", action="store_true", help="in danh sách loài/giống rồi thoát")
    args = ap.parse_args()

    if args.list:
        for sp, info in CATALOG.items():
            print("%s (%s): %s" % (sp, info["type_vi"], ", ".join(info["breeds"])))
        return

    if "your-email@example.com" in USER_AGENT:
        print("Lưu ý: hãy đổi USER_AGENT trong file thành email của bạn (Wikimedia yêu cầu).", file=sys.stderr)
    if Image is None:
        print("Lưu ý: chưa cài Pillow (pip install pillow) nên ảnh không được kiểm tra/thu nhỏ.", file=sys.stderr)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest_path = out / "manifest.csv"
    rows = load_manifest(manifest_path)
    seen_hashes = {r["sha256"] for r in rows}
    seen_ids = {r["external_id"] for r in rows}
    is_new_manifest = not manifest_path.exists()

    session = make_session()
    with manifest_path.open("a", newline="", encoding="utf-8") as mf:
        writer = csv.DictWriter(mf, fieldnames=MANIFEST_FIELDS)
        if is_new_manifest:
            writer.writeheader()

        for species, sp in CATALOG.items():
            if args.species and species not in args.species:
                continue
            for slug, br in sp["breeds"].items():
                folder = out / species / slug
                folder.mkdir(parents=True, exist_ok=True)
                have = sum(1 for r in rows if r["species"] == species and r["breed_slug"] == slug)
                need = args.per_breed - have
                if need <= 0:
                    print("[bỏ qua] %s/%s đã đủ %d ảnh" % (species, slug, have))
                    continue

                index = next_index(folder, slug)
                got = 0
                print("[tải] %s/%s: cần thêm %d ảnh" % (species, slug, need))
                for src_name, arg in br["sources"]:
                    if got >= need:
                        break
                    for cand in SOURCES[src_name](session, arg):
                        if got >= need:
                            break
                        if cand["external_id"] in seen_ids:
                            continue
                        seen_ids.add(cand["external_id"])

                        data = download(session, cand["image_url"])
                        time.sleep(args.delay)
                        if not data:
                            continue
                        digest = hashlib.sha256(data).hexdigest()
                        if digest in seen_hashes:
                            continue
                        processed = process_image(data)
                        if not processed:
                            continue
                        content, ext, w, h = processed

                        filename = "%s_%04d%s" % (slug, index, ext)
                        (folder / filename).write_bytes(content)
                        seen_hashes.add(digest)
                        index += 1
                        got += 1

                        row = {
                            "pet_type": sp["type_vi"], "species": species,
                            "breed_name": br["name"], "breed_slug": slug,
                            "file": "%s/%s/%s" % (species, slug, filename),
                            "source_type": "API", "external_id": cand["external_id"],
                            "image_url": cand["image_url"], "source_url": cand["source_url"],
                            "license": cand["license"], "author": cand["author"],
                            "sha256": digest, "width": w, "height": h,
                        }
                        writer.writerow(row)
                        mf.flush()
                        rows.append(row)
                        if got % 10 == 0:
                            print("    ... %d/%d" % (got, need))
                print("    xong %s/%s: +%d ảnh%s" % (species, slug, got,
                                                    "" if got >= need else " (nguồn không còn đủ ảnh)"))

    print("\nHoàn tất. Ảnh trong '%s', thông tin nguồn/giấy phép trong '%s'." % (out, manifest_path))


if __name__ == "__main__":
    main()