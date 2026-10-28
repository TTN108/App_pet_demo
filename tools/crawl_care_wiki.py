#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Thu thập thông tin chăm sóc thú cưng cho bảng wiki_articles.

Có 2 nguồn:
  1) wikipedia : MediaWiki API chính thức (en + vi tự động qua langlinks). Văn bản CC BY-SA 4.0,
                 được phép dùng lại nếu ghi nguồn -> script tự gắn dòng ghi nguồn vào cuối bài.
                 Kết quả mặc định status = PUBLISHED.
  2) forum     : cào diễn đàn theo cấu hình forum_sources.json (do bạn tự khai báo URL + CSS selector).
                 Chỉ lưu TRÍCH ĐOẠN NGẮN + link bài gốc, KHÔNG lưu toàn văn, KHÔNG lưu tên tác giả.
                 Tôn trọng robots.txt, nghỉ giữa các request. Kết quả mặc định status = DRAFT để bạn duyệt
                 trước khi chuyển PUBLISHED. Nội dung diễn đàn là của người đăng, hãy chỉ dùng khi điều khoản
                 của diễn đàn cho phép hoặc bạn đã xin phép.

Cài đặt:   pip install requests beautifulsoup4 lxml
Chạy:
    python crawl_care_wiki.py --source wikipedia --out wiki_out
    python crawl_care_wiki.py --source forum --forum-config forum_sources.json --out wiki_out
    python crawl_care_wiki.py --source all --species dog cat

Kết quả: <out>/wiki_articles.jsonl (mỗi dòng 1 bài) - đưa cho build_seed_db.py.
Chạy lại nhiều lần được: bài trùng (source_name + source_external_id) được ghi đè theo content_hash.
"""
import argparse
import hashlib
import json
import re
import sys
import time
import urllib.robotparser
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

import requests

from breed_meta import BREED_META, SPECIES_PAGES, SPECIES_VI, WIKI_CATEGORIES

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

# QUAN TRỌNG: đổi email thật của bạn (Wikimedia và nhiều diễn đàn yêu cầu User-Agent có liên hệ).
USER_AGENT = "AppPetDemoWikiCollector/1.0 (student project; contact: your-email@example.com)"
MAX_CONTENT_CHARS = 6000
MAX_FORUM_EXCERPT = 800
SKIP_SECTIONS = re.compile(
    r"^(see also|references?|external links?|notes?|further reading|bibliography|sources?|citations?|gallery|"
    r"in popular culture|history|liên kết ngoài|tham khảo|chú thích|xem thêm|thư mục|thư viện ảnh|lịch sử)$", re.I)

CATEGORY_NAMES = [c[0] for c in WIKI_CATEGORIES]


# ------------------------------------------------------------------ tiện ích
def make_session():
    s = requests.Session()
    s.headers.update({"User-Agent": USER_AGENT})
    return s


def get_json(session, url, params=None, retries=3):
    for attempt in range(retries):
        try:
            r = session.get(url, params=params, timeout=30)
            if r.status_code == 429:
                time.sleep(5 * (attempt + 1))
                continue
            r.raise_for_status()
            return r.json()
        except (requests.RequestException, ValueError):
            time.sleep(2 * (attempt + 1))
    return None


def clean_text(text):
    text = re.sub(r"\[\d+\]|\[citation needed\]|\[cần dẫn nguồn\]", "", text or "")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def truncate_at_paragraph(text, limit=MAX_CONTENT_CHARS):
    if len(text) <= limit:
        return text
    cut = text[:limit]
    pos = max(cut.rfind("\n\n"), cut.rfind(". "))
    return (cut[:pos + 1] if pos > limit * 0.5 else cut).rstrip() + " …"


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def summarize(text, n=220):
    first = text.strip().split("\n\n")[0].replace("\n", " ")
    return first if len(first) <= n else first[:n].rsplit(" ", 1)[0] + "…"


def classify_heading(heading):
    h = heading.lower()
    for name, _desc, keywords in WIKI_CATEGORIES:
        if any(k in h for k in keywords):
            return name
    return None


# ------------------------------------------------------------------ Wikipedia
def parse_wikitext_extract(text):
    """Tách extract dạng 'explaintext' thành (lead, [(heading, body), ...]) theo mục cấp 2."""
    lead, sections, cur_head, cur_body = [], [], None, []
    for line in (text or "").splitlines():
        m = re.match(r"^==\s*([^=].*?)\s*==\s*$", line)          # chỉ mục cấp 2: == X ==
        if m:
            if cur_head is not None:
                sections.append((cur_head, "\n".join(cur_body).strip()))
            cur_head, cur_body = m.group(1), []
            continue
        sub = re.match(r"^={3,}\s*(.*?)\s*={3,}\s*$", line)       # mục con: giữ lại như dòng tiêu đề nhỏ
        if sub:
            line = sub.group(1) + ":"
        (cur_body if cur_head is not None else lead).append(line)
    if cur_head is not None:
        sections.append((cur_head, "\n".join(cur_body).strip()))
    return clean_text("\n".join(lead)), [(h, clean_text(b)) for h, b in sections if clean_text(b)]


def fetch_wiki_page(session, lang, title, want_langlink=True):
    params = {
        "action": "query", "format": "json", "prop": "extracts|info|langlinks", "inprop": "url",
        "explaintext": 1, "exsectionformat": "wiki", "redirects": 1, "titles": title,
        "lllang": "vi", "lllimit": 1,
    }
    data = get_json(session, "https://%s.wikipedia.org/w/api.php" % lang, params=params)
    pages = ((data or {}).get("query") or {}).get("pages") or {}
    page = next(iter(pages.values()), None)
    if not page or "missing" in page or not page.get("extract"):
        return None
    vi_title = None
    if want_langlink and page.get("langlinks"):
        vi_title = page["langlinks"][0].get("*") or page["langlinks"][0].get("title")
    return {"title": page["title"], "pageid": page["pageid"], "url": page.get("fullurl", ""),
            "extract": page["extract"], "vi_title": vi_title}


def wiki_attribution(lang, page):
    return ("\n\nNguồn: Wikipedia (%s), bài \"%s\", giấy phép CC BY-SA 4.0. %s"
            % (lang, page["title"], page["url"]))


def build_articles_from_page(page, lang, pet_type_vi, breed_name, label):
    """Biến một trang Wikipedia thành nhiều bài wiki theo danh mục."""
    lead, sections = parse_wikitext_extract(page["extract"])
    buckets = {name: [] for name in CATEGORY_NAMES}
    if lead:
        buckets["Tổng quan"].append(lead)
    for head, body in sections:
        if SKIP_SECTIONS.match(head.strip()):
            continue
        cat = classify_heading(head) or "Tổng quan"
        buckets[cat].append("%s\n%s" % (head, body))
    attribution = wiki_attribution(lang, page)
    out = []
    for cat, parts in buckets.items():
        body = clean_text("\n\n".join(parts))
        if len(body) < 120:
            continue
        content = truncate_at_paragraph(body) + attribution
        out.append({
            "pet_type": pet_type_vi, "breed": breed_name, "category": cat,
            "title": "%s - %s" % (label, cat),
            "summary": summarize(body), "content": content, "image_url": None,
            "source_type": "API", "source_name": "Wikipedia (%s)" % lang,
            "source_url": page["url"],
            "source_external_id": "%s:%s:%s" % (lang, page["pageid"], cat),
            "content_hash": sha(content), "status": "PUBLISHED",
            "crawled_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        })
    return out


def crawl_wikipedia(session, species_filter, delay):
    articles = []
    for (species, slug), meta in BREED_META.items():
        if species_filter and species not in species_filter:
            continue
        pet_type_vi = SPECIES_VI[species]
        print("[wiki] %s / %s" % (species, meta["name"]))
        en = fetch_wiki_page(session, "en", meta["wiki_en"])
        time.sleep(delay)
        vi = fetch_wiki_page(session, "vi", en["vi_title"], want_langlink=False) if en and en["vi_title"] else None
        time.sleep(delay)
        label = meta["name_vi"] or meta["name"]
        got = []
        if vi:
            got += build_articles_from_page(vi, "vi", pet_type_vi, meta["name"], label)
        if en:
            # bản tiếng Anh chỉ bổ sung các danh mục mà bản tiếng Việt chưa có
            have = {a["category"] for a in got}
            got += [a for a in build_articles_from_page(en, "en", pet_type_vi, meta["name"], label)
                    if a["category"] not in have]
        if not got:
            print("   (không tìm thấy trang phù hợp)")
        articles += got

    for species, titles in SPECIES_PAGES.items():
        if species_filter and species not in species_filter:
            continue
        for title in titles:
            page = fetch_wiki_page(session, "en", title)
            time.sleep(delay)
            if not page:
                print("[wiki] bỏ qua (không có trang): %s" % title)
                continue
            print("[wiki] chung cho loài %s: %s" % (species, page["title"]))
            articles += build_articles_from_page(page, "en", SPECIES_VI[species], None,
                                                 "%s (chung)" % SPECIES_VI[species])
    return articles


# ------------------------------------------------------------------ Diễn đàn
class RobotsCache:
    def __init__(self, session):
        self.session, self.cache = session, {}

    def allowed(self, url):
        parts = urlparse(url)
        base = "%s://%s" % (parts.scheme, parts.netloc)
        if base not in self.cache:
            rp = urllib.robotparser.RobotFileParser()
            try:
                r = self.session.get(base + "/robots.txt", timeout=20)
                if r.status_code == 200:
                    rp.parse(r.text.splitlines())
                else:
                    rp.parse([])            # không có robots.txt -> không có hạn chế được khai báo
            except requests.RequestException:
                rp.parse(["User-agent: *", "Disallow: /"])   # không kiểm tra được -> thận trọng, không cào
            self.cache[base] = rp
        return self.cache[base].can_fetch(USER_AGENT, url)


def extract_forum_post(html_text, selectors):
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html_text, "lxml")
    title_el = soup.select_one(selectors.get("title", "h1"))
    post_el = soup.select_one(selectors["post"])
    if not post_el:
        return None
    for bad in post_el.select("script, style, blockquote, .bbCodeBlock, .signature, a[href^=mailto]"):
        bad.decompose()
    text = clean_text(post_el.get_text("\n"))
    text = re.sub(r"\S+@\S+", "[email]", text)                                    # không lưu email
    text = re.sub(r"(?<!\d)(\+?\d[\d .-]{8,}\d)(?!\d)", "[số]", text)              # không lưu số điện thoại
    if len(text) < 80:
        return None
    return (title_el.get_text(" ", strip=True) if title_el else "Bài viết diễn đàn"), text


def crawl_forum(session, config_path, species_filter, delay):
    cfg = json.loads(Path(config_path).read_text(encoding="utf-8"))
    robots, articles = RobotsCache(session), []
    for src in cfg:
        pet_type = src["pet_type"]
        if species_filter and pet_type not in [SPECIES_VI[s] for s in species_filter]:
            continue
        for url in src["thread_urls"]:
            if not robots.allowed(url):
                print("[forum] robots.txt không cho phép, bỏ qua: %s" % url)
                continue
            try:
                r = session.get(url, timeout=30)
                r.raise_for_status()
            except requests.RequestException as ex:
                print("[forum] lỗi tải %s: %s" % (url, ex))
                time.sleep(max(delay, 3))
                continue
            parsed = extract_forum_post(r.text, src["selectors"])
            time.sleep(max(delay, 3))                 # tối thiểu 3 giây/request với diễn đàn
            if not parsed:
                print("[forum] không trích được nội dung (kiểm tra selector): %s" % url)
                continue
            title, text = parsed
            excerpt = text if len(text) <= MAX_FORUM_EXCERPT else text[:MAX_FORUM_EXCERPT].rsplit(" ", 1)[0] + " …"
            content = excerpt + "\n\nTrích từ diễn đàn \"%s\". Đọc bài gốc đầy đủ tại: %s" % (src["name"], url)
            ext_id = sha(url)[:16]
            articles.append({
                "pet_type": pet_type, "breed": src.get("breed"), "category": src.get("category", "Tổng quan"),
                "title": title[:150], "summary": summarize(excerpt), "content": content, "image_url": None,
                "source_type": "CRAWLED", "source_name": src["name"], "source_url": url,
                "source_external_id": ext_id, "content_hash": sha(text), "status": "DRAFT",
                "crawled_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            })
            print("[forum] đã lấy: %s" % title[:70])
    return articles


# ------------------------------------------------------------------ main
def write_jsonl(path, articles):
    """Gộp với file cũ: trùng (source_name, source_external_id) thì bản mới ghi đè."""
    merged = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                a = json.loads(line)
                merged[(a["source_name"], a["source_external_id"])] = a
    for a in articles:
        merged[(a["source_name"], a["source_external_id"])] = a
    with path.open("w", encoding="utf-8") as f:
        for a in merged.values():
            f.write(json.dumps(a, ensure_ascii=False) + "\n")
    return len(merged)


def main():
    ap = argparse.ArgumentParser(description="Thu thập bài wiki chăm sóc thú cưng.")
    ap.add_argument("--source", choices=["wikipedia", "forum", "all"], default="wikipedia")
    ap.add_argument("--forum-config", default="forum_sources.json")
    ap.add_argument("--species", nargs="*", help="dog cat bird fish rabbit")
    ap.add_argument("--out", default="wiki_out")
    ap.add_argument("--delay", type=float, default=0.5)
    args = ap.parse_args()
    if "your-email@example.com" in USER_AGENT:
        print("Lưu ý: hãy đổi USER_AGENT thành email thật của bạn.", file=sys.stderr)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    session = make_session()
    articles = []
    if args.source in ("wikipedia", "all"):
        articles += crawl_wikipedia(session, args.species, args.delay)
    if args.source in ("forum", "all"):
        if Path(args.forum_config).exists():
            articles += crawl_forum(session, args.forum_config, args.species, args.delay)
        else:
            print("Không thấy %s - bỏ qua nguồn diễn đàn (xem forum_sources.example.json)." % args.forum_config)
    total = write_jsonl(out / "wiki_articles.jsonl", articles)
    print("\nXong: +%d bài, tổng %d bài trong %s" % (len(articles), total, out / "wiki_articles.jsonl"))


if __name__ == "__main__":
    main()
