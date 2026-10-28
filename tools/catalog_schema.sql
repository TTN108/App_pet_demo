-- Catalog tĩnh trong app (SQLite): loài, giống, alias, wiki. Dữ liệu động (tài khoản, thú cưng, chat, gallery) nằm trên Supabase.
-- Quy ước: mỗi câu lệnh kết thúc bằng ; ở cuối dòng, không có ; bên trong câu lệnh.
CREATE TABLE IF NOT EXISTS pet_types (
  id INTEGER PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT
);
CREATE TABLE IF NOT EXISTS breeds (
  id INTEGER PRIMARY KEY,
  pet_type_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  name_vi TEXT,
  description TEXT,
  origin_country TEXT,
  UNIQUE (pet_type_id, name),
  FOREIGN KEY (pet_type_id) REFERENCES pet_types(id)
);
CREATE TABLE IF NOT EXISTS breed_aliases (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  breed_id INTEGER NOT NULL,
  alias TEXT NOT NULL,
  UNIQUE (breed_id, alias),
  FOREIGN KEY (breed_id) REFERENCES breeds(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS wiki_categories (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  description TEXT
);
CREATE TABLE IF NOT EXISTS wiki_articles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  pet_type_id INTEGER,
  breed_id INTEGER,
  category_id INTEGER,
  title TEXT NOT NULL,
  summary TEXT,
  content TEXT NOT NULL,
  image_url TEXT,
  source_type TEXT NOT NULL DEFAULT 'ORIGINAL',
  source_name TEXT,
  source_url TEXT,
  source_external_id TEXT,
  content_hash TEXT,
  crawled_at TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  created_at TEXT,
  updated_at TEXT,
  FOREIGN KEY (pet_type_id) REFERENCES pet_types(id),
  FOREIGN KEY (breed_id) REFERENCES breeds(id),
  FOREIGN KEY (category_id) REFERENCES wiki_categories(id),
  CHECK (source_type IN ('ORIGINAL','CRAWLED','API')),
  CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
  CHECK (source_type = 'ORIGINAL' OR source_url IS NOT NULL),
  UNIQUE (source_name, source_external_id)
);
CREATE INDEX IF NOT EXISTS idx_wiki_type_status ON wiki_articles(pet_type_id, status);
CREATE INDEX IF NOT EXISTS idx_wiki_breed ON wiki_articles(breed_id);
