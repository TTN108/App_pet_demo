#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh dữ liệu danh mục dùng chung cho 2 nơi, với ID CỐ ĐỊNH giống nhau (xem breed_meta.py):
  1) App Android (SQLite, offline): app/src/main/assets/db/catalog_schema.sql, catalog_seed.sql, catalog_version.txt
     gồm loài, giống, alias nhận diện, danh mục wiki, bài wiki (bài viết tay + bài đã cào).
  2) Supabase (Postgres): supabase/003_seed_catalog.sql gồm loài, giống, alias và cộng đồng chat theo loài.
Ảnh bộ sưu tập KHÔNG nằm ở đây: dùng upload_imagekit.py + import_supabase.py.

Chạy:  python build_catalog.py --wiki wiki_out/wiki_articles.jsonl --check
"""
import argparse
import json
import shutil
import sqlite3
import sys
import time
import unicodedata
from pathlib import Path

from breed_meta import (BREED_ID, BREED_META, CATEGORY_ID, BREED_ID_BY_NAME, SPECIES_DESC, SPECIES_ID,
                        SPECIES_VI, TYPE_ID_BY_NAME, WIKI_CATEGORIES)

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
NOW = "datetime('now','localtime')"


def q(value):
    """Literal SQL một dòng (xuống dòng -> char(10); dùng được cho cả SQLite lẫn Postgres)."""
    if value is None or value == "":
        return "NULL"
    s = str(value).replace("\r", "").replace("'", "''")
    return "'" + s.replace("\n", "' || chr(10) || '") + "'"          # chỉ dùng cho Postgres; SQLite dùng q_lite


def q_lite(value):
    if value is None or value == "":
        return "NULL"
    s = str(value).replace("\r", "").replace("'", "''")
    return "'" + s.replace("\n", "' || char(10) || '") + "'"


def norm_alias(text):
    t = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    return "".join(ch for ch in t if unicodedata.category(ch) != "Mn").strip()


def breed_aliases(species, slug, m):
    al = {norm_alias(m["name"]), slug, slug.replace("-", " "), norm_alias(m["name_vi"] or m["name"])}
    al |= {norm_alias(a) for a in m["aliases"]}
    return sorted(a for a in al if a)


def sqlite_seed(articles):
    out = ["-- Seed catalog do tools/build_catalog.py tạo; mỗi dòng là một câu lệnh. Đừng sửa tay."]
    add = out.append
    for sp, name in SPECIES_VI.items():
        add("INSERT OR IGNORE INTO pet_types(id,name,description) VALUES (%d,%s,%s);"
            % (SPECIES_ID[sp], q_lite(name), q_lite(SPECIES_DESC[sp])))
    for (sp, slug), m in BREED_META.items():
        bid = BREED_ID[(sp, slug)]
        add("INSERT OR IGNORE INTO breeds(id,pet_type_id,name,name_vi,description,origin_country) VALUES (%d,%d,%s,%s,%s,%s);"
            % (bid, SPECIES_ID[sp], q_lite(m["name"]), q_lite(m["name_vi"]), q_lite(m["desc"]), q_lite(m["origin"])))
        for al in breed_aliases(sp, slug, m):
            add("INSERT OR IGNORE INTO breed_aliases(breed_id,alias) VALUES (%d,%s);" % (bid, q_lite(al)))
    for i, (name, desc, _kw) in enumerate(WIKI_CATEGORIES, start=1):
        add("INSERT OR IGNORE INTO wiki_categories(id,name,description) VALUES (%d,%s,%s);" % (i, q_lite(name), q_lite(desc)))
    for a in articles:
        type_id = TYPE_ID_BY_NAME[a["pet_type"]]
        breed_id = BREED_ID_BY_NAME[(a["pet_type"], a["breed"])] if a.get("breed") else None
        add("INSERT OR IGNORE INTO wiki_articles(pet_type_id,breed_id,category_id,title,summary,content,image_url,source_type,"
            "source_name,source_url,source_external_id,content_hash,crawled_at,status,created_at,updated_at) "
            "VALUES (%d,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s);"
            % (type_id, breed_id if breed_id else "NULL", CATEGORY_ID[a["category"]], q_lite(a["title"]),
               q_lite(a.get("summary")), q_lite(a["content"]), q_lite(a.get("image_url")), q_lite(a["source_type"]),
               q_lite(a["source_name"]), q_lite(a.get("source_url")), q_lite(a["source_external_id"]),
               q_lite(a.get("content_hash")), q_lite(a.get("crawled_at")), q_lite(a.get("status", "DRAFT")), NOW, NOW))
    return out


def postgres_seed():
    out = ["-- Seed catalog cho Supabase do tools/build_catalog.py tạo. Chạy SAU 002_security.sql. Chạy lại được (upsert)."]
    add = out.append
    for sp, name in SPECIES_VI.items():
        add("insert into public.pet_types (id, name, description) values (%d, %s, %s) "
            "on conflict (id) do update set name = excluded.name, description = excluded.description;"
            % (SPECIES_ID[sp], q(name), q(SPECIES_DESC[sp])))
    for (sp, slug), m in BREED_META.items():
        bid = BREED_ID[(sp, slug)]
        add("insert into public.breeds (id, pet_type_id, name, name_vi, description, origin_country) values (%d, %d, %s, %s, %s, %s) "
            "on conflict (id) do update set name = excluded.name, name_vi = excluded.name_vi, "
            "description = excluded.description, origin_country = excluded.origin_country;"
            % (bid, SPECIES_ID[sp], q(m["name"]), q(m["name_vi"]), q(m["desc"]), q(m["origin"])))
        for al in breed_aliases(sp, slug, m):
            add("insert into public.breed_aliases (breed_id, alias) values (%d, %s) on conflict do nothing;" % (bid, q(al)))
    for sp, name in SPECIES_VI.items():
        cid = SPECIES_ID[sp]
        add("insert into public.communities (id, pet_type_id, name, description) values (%d, %d, %s, %s) on conflict (id) do nothing;"
            % (cid, cid, q("Cộng đồng " + name),
               q("Nơi những người nuôi %s cùng trò chuyện và chia sẻ kinh nghiệm." % name.lower())))
        welcome = ("Chào mừng bạn đến với Cộng đồng %s! Hãy giữ thái độ tôn trọng, không quảng cáo, không chia sẻ thông tin cá nhân. "
                   "Xem mục Wiki để đọc hướng dẫn chăm sóc và Bộ sưu tập để tìm ảnh đẹp nhé." % name)
        add("insert into public.messages (community_id, sender_id, content, message_type) "
            "select %d, null, %s, 'SYSTEM' where not exists (select 1 from public.messages where community_id = %d);"
            % (cid, q(welcome), cid))
    add("select setval(pg_get_serial_sequence('public.communities', 'id'), greatest((select max(id) from public.communities), 1));")
    return out


def verify_sqlite(schema, seed):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript(schema.read_text(encoding="utf-8"))
    for _ in range(2):                      # chạy 2 lần: phải idempotent
        for line in seed.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("--"):
                db.execute(line)
    bad = db.execute("PRAGMA foreign_key_check").fetchall()
    counts = {t: db.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0]
              for t in ("pet_types", "breeds", "breed_aliases", "wiki_categories", "wiki_articles")}
    return bad, counts


def main():
    ap = argparse.ArgumentParser(description="Sinh catalog cho app (SQLite) và Supabase (Postgres).")
    ap.add_argument("--wiki", default="wiki_out/wiki_articles.jsonl")
    ap.add_argument("--original", default=str(HERE / "original_wiki.json"))
    ap.add_argument("--assets", default=str(ROOT / "app" / "src" / "main" / "assets" / "db"))
    ap.add_argument("--supabase", default=str(ROOT / "supabase" / "003_seed_catalog.sql"))
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    articles = []
    op = Path(args.original)
    if op.exists():
        articles += json.loads(op.read_text(encoding="utf-8"))
    wp = Path(args.wiki)
    if wp.exists():
        articles += [json.loads(l) for l in wp.read_text(encoding="utf-8").splitlines() if l.strip()]
    else:
        print("Chưa có %s - chỉ dùng bài viết tay (chạy crawl_care_wiki.py để thêm bài)." % wp, file=sys.stderr)

    assets = Path(args.assets)
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "catalog_schema.sql", assets / "catalog_schema.sql")
    (assets / "catalog_seed.sql").write_text("\n".join(sqlite_seed(articles)) + "\n", encoding="utf-8")
    # Phiên bản tăng theo giờ: app thấy số mới hơn thì tự dựng lại catalog (xem CatalogDatabase).
    version = int(time.strftime("%y%m%d%H"))
    (assets / "catalog_version.txt").write_text(str(version) + "\n", encoding="utf-8")
    sb = Path(args.supabase)
    sb.parent.mkdir(parents=True, exist_ok=True)
    sb.write_text("\n".join(postgres_seed()) + "\n", encoding="utf-8")
    print("Catalog app: %d bài wiki, phiên bản %d -> %s" % (len(articles), version, assets))
    print("Seed Supabase -> %s" % sb)
    if args.check:
        bad, counts = verify_sqlite(assets / "catalog_schema.sql", assets / "catalog_seed.sql")
        print("Kiểm tra SQLite (chạy 2 lần): vi phạm khoá ngoại %d; %s" % (len(bad), counts))
        if bad:
            sys.exit("Vi phạm khoá ngoại: %r" % bad[:5])


if __name__ == "__main__":
    main()
