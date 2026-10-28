# -*- coding: utf-8 -*-
"""Thông tin bổ sung cho từng giống (khớp CATALOG trong download_pet_images.py).

key = (species, breed_slug)  -> species/slug giống hệt thư mục ảnh pet/<species>/<slug>/
name_vi / origin khớp dữ liệu mẫu cũ trong DatabaseHelper để không đổi tên giống đã có.
aliases: tên gọi khác (không dấu, chữ thường) - dùng để map nhãn của model và tìm kiếm.
wiki_en: tiêu đề trang Wikipedia tiếng Anh (trang tiếng Việt được lấy tự động qua langlinks).
"""

SPECIES_VI = {"dog": "Chó", "cat": "Mèo", "bird": "Chim", "fish": "Cá", "rabbit": "Thỏ"}
SPECIES_DESC = {
    "dog": "Thú cưng loài chó",
    "cat": "Thú cưng loài mèo",
    "bird": "Thú cưng loài chim",
    "fish": "Thú cưng loài cá",
    "rabbit": "Thú cưng loài thỏ",
}

BREED_META = {
    ("dog", "poodle"): dict(name="Poodle", name_vi="", origin="Pháp/Đức", wiki_en="Poodle",
                            aliases=["pu do", "pudel", "cho poodle"],
                            desc="Giống chó thông minh, bộ lông xoăn ít rụng, có ba cỡ: standard, miniature và toy."),
    ("dog", "golden-retriever"): dict(name="Golden Retriever", name_vi="Golden Retriever", origin="Vương quốc Anh",
                                      wiki_en="Golden Retriever", aliases=["golden", "cho golden", "golden retriver"],
                                      desc="Giống chó hiền, thân thiện, dễ huấn luyện, bộ lông vàng dày."),
    ("dog", "corgi"): dict(name="Corgi", name_vi="Chó Corgi", origin="Vương quốc Anh",
                           wiki_en="Pembroke Welsh Corgi", aliases=["cho corgi", "welsh corgi", "pembroke"],
                           desc="Giống chó chân ngắn, thân dài, năng động và thích chăn dắt."),
    ("dog", "pug"): dict(name="Pug", name_vi="Chó Pug", origin="Trung Quốc",
                         wiki_en="Pug", aliases=["cho pug", "pug mat xe", "bac ma"],
                         desc="Giống chó nhỏ, mặt nhăn, tính tình vui vẻ, dễ khó thở khi nóng."),
    ("cat", "british-shorthair"): dict(name="British Shorthair", name_vi="Mèo Anh lông ngắn", origin="Vương quốc Anh",
                                       wiki_en="British Shorthair", aliases=["meo anh long ngan", "bsh", "meo ali"],
                                       desc="Giống mèo thân tròn, lông ngắn dày, tính điềm đạm."),
    ("cat", "persian"): dict(name="Persian", name_vi="Mèo Ba Tư", origin="Iran",
                             wiki_en="Persian cat", aliases=["meo ba tu", "persian cat"],
                             desc="Giống mèo lông dài, mặt phẳng, cần chải lông thường xuyên."),
    ("cat", "siamese"): dict(name="Siamese", name_vi="Mèo Xiêm", origin="Thái Lan",
                             wiki_en="Siamese cat", aliases=["meo xiem", "siamese cat"],
                             desc="Giống mèo mắt xanh, lông ngắn có điểm màu, hay kêu và thích tương tác."),
    ("bird", "budgerigar"): dict(name="Budgerigar", name_vi="Vẹt yến phụng", origin="Úc",
                                 wiki_en="Budgerigar", aliases=["yen phung", "vet yen phung", "budgie", "parakeet"],
                                 desc="Vẹt nhỏ sống bầy, hoạt bát, có thể học bắt chước tiếng người."),
    ("bird", "cockatiel"): dict(name="Cockatiel", name_vi="Vẹt mào", origin="Úc",
                                wiki_en="Cockatiel", aliases=["vet mao", "cockatiel bird"],
                                desc="Vẹt có mào trên đầu, hiền, thích được tương tác và huýt sáo."),
    ("fish", "betta"): dict(name="Betta", name_vi="Cá Betta", origin="Đông Nam Á",
                            wiki_en="Betta splendens", aliases=["ca betta", "ca xiem", "ca lia thia", "betta splendens"],
                            desc="Cá cảnh đuôi dài sặc sỡ, con đực hung hăng với nhau nên nuôi riêng."),
    ("fish", "goldfish"): dict(name="Goldfish", name_vi="Cá vàng", origin="Đông Á",
                               wiki_en="Goldfish", aliases=["ca vang", "ca kim", "gold fish"],
                               desc="Cá cảnh phổ biến, sống bầy, cần bể rộng và lọc tốt."),
    ("rabbit", "netherland-dwarf"): dict(name="Netherland Dwarf", name_vi="Thỏ lùn Hà Lan", origin="Hà Lan",
                                         wiki_en="Netherland Dwarf rabbit", aliases=["tho lun ha lan", "tho lun", "dwarf rabbit"],
                                         desc="Giống thỏ nhỏ nhất, tai ngắn, cần rau cỏ khô thường xuyên."),
    ("rabbit", "lionhead"): dict(name="Lionhead", name_vi="Thỏ đầu sư tử", origin="Bỉ",
                                 wiki_en="Lionhead rabbit", aliases=["tho dau su tu", "lion head", "tho lionhead"],
                                 desc="Giống thỏ có bờm lông quanh đầu như sư tử, cần chải lông đều."),
}

# Bài chung cho cả loài (breed_id = NULL). Tiêu đề en.wikipedia.org - trang không tồn tại sẽ bị bỏ qua.
SPECIES_PAGES = {
    "dog": ["Dog health", "Dog food", "Dog training", "Dog grooming"],
    "cat": ["Cat health", "Cat food", "Cat communication", "Cat grooming"],
    "bird": ["Pet bird", "Bird food", "Bird feeding"],
    "fish": ["Fishkeeping", "Aquarium", "Fish disease and parasites"],
    "rabbit": ["Domestic rabbit", "Rabbit hutch", "Rabbit health"],
}

# Danh mục wiki: (tên, mô tả, từ khóa tiêu đề mục tiếng Anh + tiếng Việt)
WIKI_CATEGORIES = [
    ("Tổng quan", "Giới thiệu chung về loài/giống", []),
    ("Dinh dưỡng", "Thức ăn và chế độ ăn", ["diet", "feeding", "nutrition", "food", "dinh dưỡng", "thức ăn", "chế độ ăn", "ăn uống"]),
    ("Sức khỏe", "Bệnh thường gặp và phòng bệnh", ["health", "disease", "illness", "genetic", "lifespan", "sức khỏe", "bệnh", "tuổi thọ", "di truyền"]),
    ("Vệ sinh & chải chuốt", "Tắm, chải lông, cắt móng", ["grooming", "coat", "care", "hygiene", "vệ sinh", "chải", "bộ lông", "lông", "chăm sóc"]),
    ("Huấn luyện & hành vi", "Tính cách, huấn luyện, hành vi", ["temperament", "training", "behavior", "behaviour", "personality", "tính cách", "tính khí", "huấn luyện", "hành vi", "đặc điểm tính"]),
    ("Môi trường sống", "Chuồng, bể, nhiệt độ, không gian", ["housing", "habitat", "tank", "aquarium", "cage", "enclosure", "environment", "môi trường", "chuồng", "bể", "lồng", "nơi ở"]),
]


# ---------------------------------------------------------------------------
# ID CỐ ĐỊNH, dùng chung cho catalog trong app (SQLite) và Supabase (Postgres).
# KHÔNG đổi thứ tự/ID đã dùng: pets, gallery_images trên server tham chiếu các ID này.
# Muốn thêm loài/giống: chỉ thêm vào CUỐI SPECIES_VI / BREED_META.
# ---------------------------------------------------------------------------
SPECIES_ID = {sp: i for i, sp in enumerate(SPECIES_VI, start=1)}            # dog=1 cat=2 bird=3 fish=4 rabbit=5
BREED_ID = {key: i for i, key in enumerate(BREED_META, start=1)}            # (species, slug) -> 1..13
TYPE_ID_BY_NAME = {name: SPECIES_ID[sp] for sp, name in SPECIES_VI.items()}  # "Chó" -> 1
BREED_ID_BY_NAME = {(SPECIES_VI[sp], m["name"]): BREED_ID[(sp, slug)] for (sp, slug), m in BREED_META.items()}
CATEGORY_ID = {name: i for i, (name, _d, _k) in enumerate(WIKI_CATEGORIES, start=1)}
