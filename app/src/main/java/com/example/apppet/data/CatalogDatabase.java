package com.example.apppet.data;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.apppet.model.Breed;
import com.example.apppet.model.BreedRef;
import com.example.apppet.model.PetType;
import com.example.apppet.model.WikiArticle;
import com.example.apppet.model.WikiCategory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalog tĩnh lưu trong app (SQLite, dùng được khi không có mạng): loài, giống, alias nhận diện, wiki chăm sóc.
 * Dữ liệu do tools/build_catalog.py sinh ra (assets/db/catalog_*.sql). ID loài/giống GIỐNG HỆT trên Supabase.
 * Phiên bản lấy từ assets/db/catalog_version.txt: chạy lại build_catalog.py rồi build app thì catalog tự dựng lại.
 */
public class CatalogDatabase extends SQLiteOpenHelper {
    private static final String TAG = "CatalogDatabase";
    private static final String DATABASE_NAME = "app_pet_catalog.db";
    private static final String SCHEMA_ASSET = "db/catalog_schema.sql";
    private static final String SEED_ASSET = "db/catalog_seed.sql";
    private static final String VERSION_ASSET = "db/catalog_version.txt";
    private static final String[] TABLES = {"wiki_articles", "wiki_categories", "breed_aliases", "breeds", "pet_types"};

    private static CatalogDatabase instance;
    private final Context appContext;
    private Map<Long, String> typeNames;
    private Map<Long, String> breedNames;
    private Map<Long, String> categoryNames;

    public static synchronized CatalogDatabase getInstance(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new CatalogDatabase(app, readVersion(app));
        }
        return instance;
    }

    private CatalogDatabase(Context context, int version) {
        super(context, DATABASE_NAME, null, version);
        this.appContext = context;
    }

    private static int readVersion(Context context) {
        try (InputStream in = context.getAssets().open(VERSION_ASSET);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return Math.max(1, Integer.parseInt(r.readLine().trim()));
        } catch (IOException | RuntimeException ex) {
            return 1;
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        StringBuilder sql = new StringBuilder();
        for (String line : readAssetLines(SCHEMA_ASSET)) {
            if (!line.trim().startsWith("--")) sql.append(line).append('\n');
        }
        for (String statement : sql.toString().split(";")) {
            String s = statement.trim();
            if (!s.isEmpty()) db.execSQL(s);
        }
        for (String line : readAssetLines(SEED_ASSET)) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("--")) continue;
            try {
                db.execSQL(s);
            } catch (SQLException ex) {
                Log.w(TAG, "Bỏ qua câu lệnh seed lỗi: " + s.substring(0, Math.min(80, s.length())), ex);
            }
        }
    }

    /** Dữ liệu tĩnh nên nâng/hạ phiên bản đều dựng lại từ đầu. */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        rebuild(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        rebuild(db);
    }

    private void rebuild(SQLiteDatabase db) {
        for (String t : TABLES) db.execSQL("DROP TABLE IF EXISTS " + t);
        onCreate(db);
        typeNames = null;
        breedNames = null;
        categoryNames = null;
    }

    private List<String> readAssetLines(String path) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = appContext.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } catch (IOException ex) {
            Log.e(TAG, "Không đọc được asset " + path, ex);
        }
        return lines;
    }

    // ===================== Loài / giống =====================

    public List<PetType> getPetTypes() {
        List<PetType> items = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, name FROM pet_types ORDER BY id", null);
        try {
            while (c.moveToNext()) items.add(new PetType(c.getLong(0), c.getString(1)));
        } finally {
            c.close();
        }
        return items;
    }

    public List<Breed> getBreedsByPetType(long petTypeId) {
        List<Breed> items = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, pet_type_id, name, name_vi FROM breeds WHERE pet_type_id = ? " +
                        "ORDER BY COALESCE(NULLIF(name_vi, ''), name) COLLATE NOCASE",
                new String[]{String.valueOf(petTypeId)});
        try {
            while (c.moveToNext()) items.add(new Breed(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)));
        } finally {
            c.close();
        }
        return items;
    }

    /** Tên loài theo id (cache trong bộ nhớ, tránh truy vấn lặp khi hiển thị danh sách thú cưng). */
    public synchronized String petTypeName(long id) {
        if (typeNames == null) {
            typeNames = new HashMap<>();
            for (PetType t : getPetTypes()) typeNames.put(t.id, t.name);
        }
        String n = typeNames.get(id);
        return n != null ? n : "Khác";
    }

    /** Tên hiển thị của giống: "Tên Việt (Tên gốc)" nếu có, null nếu không biết giống. */
    public synchronized String breedDisplayName(Long breedId) {
        if (breedId == null) return null;
        if (breedNames == null) {
            breedNames = new HashMap<>();
            Cursor c = getReadableDatabase().rawQuery("SELECT id, name, name_vi FROM breeds", null);
            try {
                while (c.moveToNext()) {
                    String en = c.getString(1), vi = c.getString(2);
                    breedNames.put(c.getLong(0), vi != null && !vi.trim().isEmpty() && !vi.equalsIgnoreCase(en)
                            ? vi + " (" + en + ")" : en);
                }
            } finally {
                c.close();
            }
        }
        return breedNames.get(breedId);
    }

    /** Map nhãn của model (vd "golden-retriever") hoặc tên người dùng nhập về một giống, qua breed_aliases. */
    public BreedRef findBreedByLabel(String label) {
        if (label == null || label.trim().isEmpty()) return null;
        String key = label.trim().toLowerCase(Locale.ROOT);
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT b.id, b.pet_type_id, b.name, b.name_vi, pt.name " +
                        "FROM breed_aliases a INNER JOIN breeds b ON b.id = a.breed_id " +
                        "INNER JOIN pet_types pt ON pt.id = b.pet_type_id " +
                        "WHERE a.alias = ? OR a.alias = ? LIMIT 1",
                new String[]{key, key.replace('-', ' ')});
        try {
            if (!c.moveToFirst()) return null;
            return new BreedRef(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4));
        } finally {
            c.close();
        }
    }

    // ===================== Wiki chăm sóc =====================

    public List<WikiCategory> getWikiCategories() {
        List<WikiCategory> items = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, name FROM wiki_categories ORDER BY id", null);
        try {
            while (c.moveToNext()) items.add(new WikiCategory(c.getLong(0), c.getString(1)));
        } finally {
            c.close();
        }
        return items;
    }

    public synchronized String wikiCategoryName(Long id) {
        if (id == null) return null;
        if (categoryNames == null) {
            categoryNames = new HashMap<>();
            for (WikiCategory wc : getWikiCategories()) categoryNames.put(wc.id, wc.name);
        }
        return categoryNames.get(id);
    }

    private static final String WIKI_SELECT =
            "SELECT w.id, w.pet_type_id, w.breed_id, w.category_id, wc.name, pt.name, b.name, w.title, w.summary, " +
                    "w.content, w.image_url, w.source_type, w.source_name, w.source_url, w.crawled_at " +
                    "FROM wiki_articles w LEFT JOIN wiki_categories wc ON wc.id = w.category_id " +
                    "LEFT JOIN pet_types pt ON pt.id = w.pet_type_id LEFT JOIN breeds b ON b.id = w.breed_id " +
                    "WHERE w.status = 'PUBLISHED'";

    /**
     * Bài wiki đã PUBLISHED. petTypeId = null: mọi loài. breedId != null: bài của giống đó + bài chung cho loài,
     * bài đúng giống xếp trước. query: tìm trong tiêu đề/tóm tắt/nội dung.
     */
    public List<WikiArticle> getWikiArticles(Long petTypeId, Long breedId, String query, int limit) {
        StringBuilder sql = new StringBuilder(WIKI_SELECT);
        List<String> args = new ArrayList<>();
        if (petTypeId != null) { sql.append(" AND w.pet_type_id = ?"); args.add(String.valueOf(petTypeId)); }
        if (breedId != null) { sql.append(" AND (w.breed_id = ? OR w.breed_id IS NULL)"); args.add(String.valueOf(breedId)); }
        if (query != null && !query.trim().isEmpty()) {
            String like = "%" + query.trim().replace("%", "").replace("_", "") + "%";
            sql.append(" AND (w.title LIKE ? OR w.summary LIKE ? OR w.content LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY ");
        if (breedId != null) { sql.append("CASE WHEN w.breed_id = ? THEN 0 ELSE 1 END, "); args.add(String.valueOf(breedId)); }
        sql.append("COALESCE(w.category_id, 99), w.id LIMIT ?");
        args.add(String.valueOf(limit));

        List<WikiArticle> items = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) items.add(readWiki(c));
        } finally {
            c.close();
        }
        return items;
    }

    public WikiArticle getWikiArticle(long id) {
        Cursor c = getReadableDatabase().rawQuery(WIKI_SELECT + " AND w.id = ? LIMIT 1", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readWiki(c) : null;
        } finally {
            c.close();
        }
    }

    private WikiArticle readWiki(Cursor c) {
        WikiArticle w = new WikiArticle();
        w.id = c.getLong(0);
        w.petTypeId = c.isNull(1) ? null : c.getLong(1);
        w.breedId = c.isNull(2) ? null : c.getLong(2);
        w.categoryId = c.isNull(3) ? null : c.getLong(3);
        w.categoryName = c.getString(4);
        w.petTypeName = c.getString(5);
        w.breedName = c.getString(6);
        w.title = c.getString(7);
        w.summary = c.getString(8);
        w.content = c.getString(9);
        w.imageUrl = c.getString(10);
        w.sourceType = c.getString(11);
        w.sourceName = c.getString(12);
        w.sourceUrl = c.getString(13);
        w.crawledAt = c.getString(14);
        return w;
    }
}
