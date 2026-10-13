package com.example.apppet.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.apppet.model.Breed;
import com.example.apppet.model.Pet;
import com.example.apppet.model.PetType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** SQLite cục bộ theo cấu trúc App Pet Demo phiên bản 2. */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "app_pet_demo_v2.db";
    public static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "email TEXT UNIQUE NOT NULL," +
                "password_hash TEXT NOT NULL," +
                "display_name TEXT," +
                "avatar_uri TEXT," +
                "role TEXT NOT NULL DEFAULT 'USER'," +
                "created_at TEXT," +
                "updated_at TEXT)");

        db.execSQL("CREATE TABLE pet_types (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE NOT NULL," +
                "description TEXT," +
                "icon_uri TEXT)");

        db.execSQL("CREATE TABLE breeds (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_type_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "name_vi TEXT," +
                "description TEXT," +
                "origin_country TEXT," +
                "image_uri TEXT," +
                "created_at TEXT," +
                "UNIQUE (pet_type_id, name)," +
                "UNIQUE (id, pet_type_id)," +
                "FOREIGN KEY (pet_type_id) REFERENCES pet_types(id))");

        db.execSQL("CREATE TABLE breed_aliases (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "breed_id INTEGER NOT NULL," +
                "alias TEXT NOT NULL," +
                "UNIQUE (breed_id, alias)," +
                "FOREIGN KEY (breed_id) REFERENCES breeds(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE pets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "pet_type_id INTEGER NOT NULL," +
                "breed_id INTEGER," +
                "name TEXT NOT NULL," +
                "gender TEXT," +
                "birth_date TEXT," +
                "color TEXT," +
                "weight REAL," +
                "description TEXT," +
                "created_at TEXT," +
                "updated_at TEXT," +
                "FOREIGN KEY (user_id) REFERENCES users(id)," +
                "FOREIGN KEY (pet_type_id) REFERENCES pet_types(id)," +
                "FOREIGN KEY (breed_id, pet_type_id) REFERENCES breeds(id, pet_type_id))");

        db.execSQL("CREATE TABLE pet_images (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_id INTEGER NOT NULL," +
                "image_uri TEXT NOT NULL," +
                "is_main INTEGER DEFAULT 0," +
                "created_at TEXT," +
                "FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE recognition_results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_image_id INTEGER," +
                "detected_pet_type_id INTEGER," +
                "detected_breed_id INTEGER," +
                "confidence REAL," +
                "model_name TEXT," +
                "raw_label TEXT," +
                "result_json TEXT," +
                "created_at TEXT," +
                "FOREIGN KEY (pet_image_id) REFERENCES pet_images(id)," +
                "FOREIGN KEY (detected_pet_type_id) REFERENCES pet_types(id)," +
                "FOREIGN KEY (detected_breed_id, detected_pet_type_id) REFERENCES breeds(id, pet_type_id))");

        db.execSQL("CREATE TABLE gallery_images (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_type_id INTEGER," +
                "breed_id INTEGER," +
                "source_type TEXT NOT NULL," +
                "external_id TEXT," +
                "pet_image_id INTEGER," +
                "contributor_id INTEGER," +
                "status TEXT NOT NULL DEFAULT 'PENDING'," +
                "reviewed_by INTEGER," +
                "reviewed_at TEXT," +
                "reject_reason TEXT," +
                "image_url TEXT NOT NULL," +
                "title TEXT," +
                "description TEXT," +
                "source_url TEXT," +
                "created_at TEXT," +
                "updated_at TEXT," +
                "FOREIGN KEY (pet_type_id) REFERENCES pet_types(id)," +
                "FOREIGN KEY (breed_id, pet_type_id) REFERENCES breeds(id, pet_type_id)," +
                "FOREIGN KEY (pet_image_id) REFERENCES pet_images(id) ON DELETE CASCADE," +
                "FOREIGN KEY (contributor_id) REFERENCES users(id) ON DELETE SET NULL," +
                "FOREIGN KEY (reviewed_by) REFERENCES users(id)," +
                "CHECK (source_type IN ('API','USER','ADMIN'))," +
                "CHECK (status IN ('PENDING','APPROVED','REJECTED'))," +
                "CHECK (source_type <> 'USER' OR contributor_id IS NOT NULL)," +
                "UNIQUE (source_type, external_id))");

        db.execSQL("CREATE TABLE communities (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_type_id INTEGER," +
                "created_by INTEGER," +
                "name TEXT NOT NULL," +
                "description TEXT," +
                "image_uri TEXT," +
                "created_at TEXT," +
                "FOREIGN KEY (pet_type_id) REFERENCES pet_types(id)," +
                "FOREIGN KEY (created_by) REFERENCES users(id))");

        db.execSQL("CREATE TABLE community_members (" +
                "community_id INTEGER NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "role TEXT NOT NULL DEFAULT 'MEMBER'," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "muted_until TEXT," +
                "joined_at TEXT," +
                "PRIMARY KEY (community_id, user_id)," +
                "FOREIGN KEY (community_id) REFERENCES communities(id)," +
                "FOREIGN KEY (user_id) REFERENCES users(id)," +
                "CHECK (role IN ('MEMBER','MOD','ADMIN'))," +
                "CHECK (status IN ('ACTIVE','MUTED','BANNED')))");

        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "community_id INTEGER," +
                "sender_id INTEGER," +
                "content TEXT," +
                "image_uri TEXT," +
                "message_type TEXT DEFAULT 'TEXT'," +
                "created_at TEXT," +
                "is_deleted INTEGER DEFAULT 0," +
                "deleted_at TEXT," +
                "deleted_by INTEGER," +
                "delete_reason TEXT," +
                "FOREIGN KEY (community_id) REFERENCES communities(id)," +
                "FOREIGN KEY (sender_id) REFERENCES users(id)," +
                "FOREIGN KEY (deleted_by) REFERENCES users(id))");

        db.execSQL("CREATE TABLE message_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id INTEGER NOT NULL," +
                "reporter_id INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "detail TEXT," +
                "status TEXT NOT NULL DEFAULT 'PENDING'," +
                "handled_by INTEGER," +
                "handled_at TEXT," +
                "action_taken TEXT," +
                "created_at TEXT," +
                "UNIQUE (message_id, reporter_id)," +
                "FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE," +
                "FOREIGN KEY (reporter_id) REFERENCES users(id)," +
                "FOREIGN KEY (handled_by) REFERENCES users(id))");

        db.execSQL("CREATE TABLE wiki_categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "description TEXT)");

        db.execSQL("CREATE TABLE wiki_articles (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pet_type_id INTEGER," +
                "breed_id INTEGER," +
                "category_id INTEGER," +
                "title TEXT NOT NULL," +
                "summary TEXT," +
                "content TEXT NOT NULL," +
                "image_url TEXT," +
                "source_type TEXT NOT NULL DEFAULT 'ORIGINAL'," +
                "source_name TEXT," +
                "source_url TEXT," +
                "source_external_id TEXT," +
                "content_hash TEXT," +
                "crawled_at TEXT," +
                "status TEXT NOT NULL DEFAULT 'DRAFT'," +
                "created_at TEXT," +
                "updated_at TEXT," +
                "FOREIGN KEY (pet_type_id) REFERENCES pet_types(id)," +
                "FOREIGN KEY (breed_id, pet_type_id) REFERENCES breeds(id, pet_type_id)," +
                "FOREIGN KEY (category_id) REFERENCES wiki_categories(id)," +
                "CHECK (source_type IN ('ORIGINAL','CRAWLED','API'))," +
                "CHECK (source_type = 'ORIGINAL' OR source_url IS NOT NULL)," +
                "UNIQUE (source_name, source_external_id))");

        seedDemoUser(db);
        seedPetTypesAndBreeds(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Không xóa dữ liệu người dùng khi nâng cấp.
        // Khi sửa cấu trúc DB, bổ sung migration tương ứng tại đây.
    }

    private void seedDemoUser(SQLiteDatabase db) {
        String now = now();
        ContentValues values = new ContentValues();
        values.put("username", "demo");
        values.put("email", "demo@example.com");
        values.put("password_hash", "DEMO_ONLY_NOT_FOR_AUTHENTICATION");
        values.put("display_name", "Người dùng Demo");
        values.put("role", "USER");
        values.put("created_at", now);
        values.put("updated_at", now);
        db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void seedPetTypesAndBreeds(SQLiteDatabase db) {
        addTypeIfMissing(db, "Chó", "Thú cưng loài chó");
        addTypeIfMissing(db, "Mèo", "Thú cưng loài mèo");
        addTypeIfMissing(db, "Chim", "Thú cưng loài chim");
        addTypeIfMissing(db, "Cá", "Thú cưng loài cá");
        addTypeIfMissing(db, "Thỏ", "Thú cưng loài thỏ");

        addBreedIfMissing(db, "Chó", "Poodle", "", "Pháp/Đức");
        addBreedIfMissing(db, "Chó", "Golden Retriever", "Golden Retriever", "Vương quốc Anh");
        addBreedIfMissing(db, "Chó", "Corgi", "Chó Corgi", "Vương quốc Anh");
        addBreedIfMissing(db, "Chó", "Pug", "Chó Pug", "Trung Quốc");
        addBreedIfMissing(db, "Mèo", "British Shorthair", "Mèo Anh lông ngắn", "Vương quốc Anh");
        addBreedIfMissing(db, "Mèo", "Persian", "Mèo Ba Tư", "Iran");
        addBreedIfMissing(db, "Mèo", "Siamese", "Mèo Xiêm", "Thái Lan");
        addBreedIfMissing(db, "Chim", "Budgerigar", "Vẹt yến phụng", "Úc");
        addBreedIfMissing(db, "Chim", "Cockatiel", "Vẹt mào", "Úc");
        addBreedIfMissing(db, "Cá", "Betta", "Cá Betta", "Đông Nam Á");
        addBreedIfMissing(db, "Cá", "Goldfish", "Cá vàng", "Đông Á");
        addBreedIfMissing(db, "Thỏ", "Netherland Dwarf", "Thỏ lùn Hà Lan", "Hà Lan");
        addBreedIfMissing(db, "Thỏ", "Lionhead", "Thỏ đầu sư tử", "Bỉ");
    }

    private void addTypeIfMissing(SQLiteDatabase db, String name, String description) {
        if (findId(db, "pet_types", "name", name) != -1) return;
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("description", description);
        db.insert("pet_types", null, values);
    }

    private void addBreedIfMissing(SQLiteDatabase db, String typeName, String name, String nameVi, String origin) {
        long typeId = findId(db, "pet_types", "name", typeName);
        if (typeId == -1 || findBreedId(db, typeId, name) != -1) return;
        ContentValues values = new ContentValues();
        values.put("pet_type_id", typeId);
        values.put("name", name);
        if (nameVi != null && !nameVi.trim().isEmpty()) values.put("name_vi", nameVi);
        values.put("origin_country", origin);
        values.put("created_at", now());
        db.insert("breeds", null, values);
    }

    private long findId(SQLiteDatabase db, String table, String column, String value) {
        Cursor cursor = db.rawQuery("SELECT id FROM " + table + " WHERE " + column + " = ? LIMIT 1", new String[]{value});
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        } finally {
            cursor.close();
        }
    }

    private long findBreedId(SQLiteDatabase db, long typeId, String name) {
        Cursor cursor = db.rawQuery("SELECT id FROM breeds WHERE pet_type_id = ? AND name = ? LIMIT 1",
                new String[]{String.valueOf(typeId), name});
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        } finally {
            cursor.close();
        }
    }

    public long getDemoUserId() {
        SQLiteDatabase db = getReadableDatabase();
        long id = findId(db, "users", "username", "demo");
        if (id != -1L) return id;
        // Phòng trường hợp database được nhập từ nơi khác mà chưa có tài khoản demo.
        seedDemoUser(getWritableDatabase());
        return findId(getReadableDatabase(), "users", "username", "demo");
    }

    public List<PetType> getPetTypes() {
        List<PetType> items = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name FROM pet_types ORDER BY name COLLATE NOCASE", null);
        try {
            while (cursor.moveToNext()) items.add(new PetType(cursor.getLong(0), cursor.getString(1)));
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<Breed> getBreedsByPetType(long petTypeId) {
        List<Breed> items = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, pet_type_id, name, name_vi FROM breeds WHERE pet_type_id = ? ORDER BY COALESCE(NULLIF(name_vi, ''), name) COLLATE NOCASE",
                new String[]{String.valueOf(petTypeId)});
        try {
            while (cursor.moveToNext()) {
                items.add(new Breed(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3)));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<Pet> getPetsForUser(long userId) {
        List<Pet> items = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(PET_SELECT +
                " WHERE p.user_id = ? ORDER BY COALESCE(p.updated_at, p.created_at, '') DESC, p.id DESC",
                new String[]{String.valueOf(userId)});
        try {
            while (cursor.moveToNext()) items.add(readPet(cursor));
        } finally {
            cursor.close();
        }
        return items;
    }

    public Pet getPetForUser(long petId, long userId) {
        Cursor cursor = getReadableDatabase().rawQuery(PET_SELECT + " WHERE p.id = ? AND p.user_id = ? LIMIT 1",
                new String[]{String.valueOf(petId), String.valueOf(userId)});
        try {
            return cursor.moveToFirst() ? readPet(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private static final String PET_SELECT =
            "SELECT p.id, p.user_id, p.pet_type_id, p.breed_id, p.name, p.gender, p.birth_date, " +
            "p.color, p.weight, p.description, p.created_at, p.updated_at, pt.name AS pet_type_name, " +
            "CASE WHEN b.name_vi IS NOT NULL AND TRIM(b.name_vi) <> '' AND b.name_vi <> b.name " +
            "THEN b.name_vi || ' (' || b.name || ')' ELSE b.name END AS breed_name, " +
            "(SELECT pi.image_uri FROM pet_images pi WHERE pi.pet_id = p.id " +
            "ORDER BY pi.is_main DESC, pi.id DESC LIMIT 1) AS avatar_uri " +
            "FROM pets p INNER JOIN pet_types pt ON pt.id = p.pet_type_id " +
            "LEFT JOIN breeds b ON b.id = p.breed_id AND b.pet_type_id = p.pet_type_id";

    private Pet readPet(Cursor cursor) {
        Pet pet = new Pet();
        pet.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        pet.userId = cursor.getLong(cursor.getColumnIndexOrThrow("user_id"));
        pet.petTypeId = cursor.getLong(cursor.getColumnIndexOrThrow("pet_type_id"));
        int breedColumn = cursor.getColumnIndexOrThrow("breed_id");
        pet.breedId = cursor.isNull(breedColumn) ? null : cursor.getLong(breedColumn);
        pet.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        pet.gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
        pet.birthDate = cursor.getString(cursor.getColumnIndexOrThrow("birth_date"));
        pet.color = cursor.getString(cursor.getColumnIndexOrThrow("color"));
        int weightColumn = cursor.getColumnIndexOrThrow("weight");
        pet.weight = cursor.isNull(weightColumn) ? null : cursor.getDouble(weightColumn);
        pet.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
        pet.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
        pet.updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"));
        pet.petTypeName = cursor.getString(cursor.getColumnIndexOrThrow("pet_type_name"));
        pet.breedName = cursor.getString(cursor.getColumnIndexOrThrow("breed_name"));
        pet.avatarUri = cursor.getString(cursor.getColumnIndexOrThrow("avatar_uri"));
        return pet;
    }

    public long insertPet(long userId, long petTypeId, Long breedId, String name,
                          String gender, String birthDate, String color, Double weight, String description) {
        String now = now();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("pet_type_id", petTypeId);
        if (breedId == null) values.putNull("breed_id"); else values.put("breed_id", breedId);
        values.put("name", name.trim());
        putNullable(values, "gender", gender);
        putNullable(values, "birth_date", birthDate);
        putNullable(values, "color", color);
        if (weight == null) values.putNull("weight"); else values.put("weight", weight);
        putNullable(values, "description", description);
        values.put("created_at", now);
        values.put("updated_at", now);
        try {
            return getWritableDatabase().insertOrThrow("pets", null, values);
        } catch (SQLiteException ex) {
            return -1L;
        }
    }


    /** Cập nhật hồ sơ, đồng thời kiểm tra thú cưng thuộc đúng tài khoản hiện tại. */
    public boolean updatePetForUser(long petId, long userId, long petTypeId, Long breedId, String name,
                                    String gender, String birthDate, String color, Double weight, String description) {
        ContentValues values = new ContentValues();
        values.put("pet_type_id", petTypeId);
        if (breedId == null) values.putNull("breed_id"); else values.put("breed_id", breedId);
        values.put("name", name.trim());
        putNullable(values, "gender", gender);
        putNullable(values, "birth_date", birthDate);
        putNullable(values, "color", color);
        if (weight == null) values.putNull("weight"); else values.put("weight", weight);
        putNullable(values, "description", description);
        values.put("updated_at", now());
        try {
            return getWritableDatabase().update("pets", values, "id = ? AND user_id = ?",
                    new String[]{String.valueOf(petId), String.valueOf(userId)}) == 1;
        } catch (SQLiteException ex) {
            return false;
        }
    }

    /** Lưu ảnh avatar trong pet_images; image_uri trỏ tới file ảnh lưu riêng trong bộ nhớ ứng dụng. */
    public boolean savePetAvatarForUser(long petId, long userId, String imageUri) {
        if (imageUri == null || imageUri.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        Cursor owner = db.rawQuery("SELECT id FROM pets WHERE id = ? AND user_id = ? LIMIT 1",
                new String[]{String.valueOf(petId), String.valueOf(userId)});
        try {
            if (!owner.moveToFirst()) return false;
        } finally {
            owner.close();
        }

        Cursor current = db.rawQuery("SELECT id, image_uri FROM pet_images WHERE pet_id = ? AND is_main = 1 " +
                        "ORDER BY id DESC LIMIT 1", new String[]{String.valueOf(petId)});
        try {
            if (current.moveToFirst() && imageUri.equals(current.getString(1))) return true;
        } finally {
            current.close();
        }

        db.beginTransaction();
        try {
            ContentValues clearMain = new ContentValues();
            clearMain.put("is_main", 0);
            db.update("pet_images", clearMain, "pet_id = ?", new String[]{String.valueOf(petId)});

            ContentValues image = new ContentValues();
            image.put("pet_id", petId);
            image.put("image_uri", imageUri);
            image.put("is_main", 1);
            image.put("created_at", now());
            long inserted = db.insertOrThrow("pet_images", null, image);
            db.setTransactionSuccessful();
            return inserted > 0;
        } catch (SQLiteException ex) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private void putNullable(ContentValues values, String key, String value) {
        if (value == null || value.trim().isEmpty()) values.putNull(key);
        else values.put(key, value.trim());
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }
}
