package com.example.apppet.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.apppet.model.Breed;
import com.example.apppet.model.CareGuide;
import com.example.apppet.model.Category;
import com.example.apppet.model.Community;
import com.example.apppet.model.ForumPost;
import com.example.apppet.model.GalleryItem;
import com.example.apppet.model.Membership;
import com.example.apppet.model.Message;
import com.example.apppet.model.Pet;
import com.example.apppet.model.PetType;
import com.example.apppet.model.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** SQLite cục bộ hợp nhất cho App Pet Demo (gốc data package). */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "app_pet_demo_v2.db";
    public static final int DATABASE_VERSION = 2;

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

        db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
        db.execSQL("CREATE TABLE care_guides (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, title TEXT, subtitle TEXT, content TEXT)");
        db.execSQL("CREATE TABLE forum_posts (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, group_name TEXT, time_ago TEXT, content TEXT)");
        db.execSQL("CREATE TABLE gallery_items (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, image_uri TEXT, color_hex TEXT, is_download INTEGER)");

        seedDemoUser(db);
        seedPetTypesAndBreeds(db);
        seedLegacyData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS care_guides (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, title TEXT, subtitle TEXT, content TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS forum_posts (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, group_name TEXT, time_ago TEXT, content TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS gallery_items (id INTEGER PRIMARY KEY AUTOINCREMENT, category_id INTEGER, image_uri TEXT, color_hex TEXT, is_download INTEGER)");
                seedLegacyData(db);
            } catch (Exception ignored) {}
        }
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

    private void seedLegacyData(SQLiteDatabase db) {
        addCategoryIfMissing(db, 1, "Mèo");
        addCategoryIfMissing(db, 2, "Chó");
        addCategoryIfMissing(db, 3, "Chim");
        addCategoryIfMissing(db, 4, "Thỏ");
        addCategoryIfMissing(db, 5, "Cá");

        ContentValues g1 = new ContentValues();
        g1.put("category_id", 1);
        g1.put("title", "Cách tắm cho mèo đúng cách");
        g1.put("subtitle", "Vệ sinh · 3 phút đọc");
        g1.put("content", "Mèo rất sợ nước, hãy chuẩn bị nước ấm khoảng 38 độ C...");
        db.insert("care_guides", null, g1);

        ContentValues g2 = new ContentValues();
        g2.put("category_id", 2);
        g2.put("title", "Chế độ dinh dưỡng cho cún cún");
        g2.put("subtitle", "Dinh dưỡng · 5 phút đọc");
        g2.put("content", "Chó cần protein chất lượng cao...");
        db.insert("care_guides", null, g2);

        ContentValues f1 = new ContentValues();
        f1.put("category_id", 1);
        f1.put("group_name", "Hội yêu mèo");
        f1.put("time_ago", "2 giờ trước");
        f1.put("content", "Bé nhà mình mới đổi thức ăn, có ai gặp tình trạng biếng ăn không?");
        db.insert("forum_posts", null, f1);

        ContentValues f2 = new ContentValues();
        f2.put("category_id", 2);
        f2.put("group_name", "Hội yêu cún");
        f2.put("time_ago", "1 giờ trước");
        f2.put("content", "Kinh nghiệm huấn luyện chó Poodle đi vệ sinh đúng chỗ cực kỳ hiệu quả!");
        db.insert("forum_posts", null, f2);

        ContentValues gal1 = new ContentValues();
        gal1.put("category_id", 1);
        gal1.put("image_uri", "");
        gal1.put("color_hex", "#E6F4EA");
        gal1.put("is_download", 0);
        db.insert("gallery_items", null, gal1);

        ContentValues gal2 = new ContentValues();
        gal2.put("category_id", 1);
        gal2.put("image_uri", "");
        gal2.put("color_hex", "#FEF7E0");
        gal2.put("is_download", 0);
        db.insert("gallery_items", null, gal2);
    }

    private void addCategoryIfMissing(SQLiteDatabase db, int id, String name) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("name", name);
        db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
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

    // ===================== User Accounts =====================

    public boolean usernameExists(String username) {
        return findId(getReadableDatabase(), "users", "username", username.trim().toLowerCase(Locale.ROOT)) != -1L;
    }

    public boolean emailExists(String email) {
        return findId(getReadableDatabase(), "users", "email", email.trim().toLowerCase(Locale.ROOT)) != -1L;
    }

    public long registerUser(String username, String email, String password, String displayName) {
        String now = now();
        String cleanUsername = username.trim().toLowerCase(Locale.ROOT);
        ContentValues values = new ContentValues();
        values.put("username", cleanUsername);
        values.put("email", email.trim().toLowerCase(Locale.ROOT));
        values.put("password_hash", PasswordHasher.hash(password));
        values.put("display_name", displayName == null || displayName.trim().isEmpty()
                ? cleanUsername : displayName.trim());
        values.put("role", "USER");
        values.put("created_at", now);
        values.put("updated_at", now);
        try {
            return getWritableDatabase().insertOrThrow("users", null, values);
        } catch (SQLiteException ex) {
            return -1L;
        }
    }

    public User authenticate(String login, String password) {
        if (login == null || password == null) return null;
        String key = login.trim().toLowerCase(Locale.ROOT);
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, username, email, display_name, role, password_hash, avatar_uri FROM users " +
                        "WHERE username = ? OR email = ? LIMIT 1", new String[]{key, key});
        try {
            if (!cursor.moveToFirst()) return null;
            String storedHash = cursor.getString(5);
            if (!PasswordHasher.verify(password, storedHash)) return null;
            return readUser(cursor);
        } finally {
            cursor.close();
        }
    }

    public User getUser(long userId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, username, email, display_name, role, password_hash, avatar_uri FROM users WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(userId)});
        try {
            return cursor.moveToFirst() ? readUser(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private User readUser(Cursor cursor) {
        String hash = cursor.getString(5);
        boolean placeholder = hash == null || !hash.startsWith("pbkdf2$");
        User user = new User(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), placeholder);
        user.avatarUri = cursor.getString(6);
        return user;
    }

    public boolean updateUserAvatar(long userId, String avatarUri) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("avatar_uri", avatarUri);
        return db.update("users", values, "id = ?", new String[]{String.valueOf(userId)}) > 0;
    }

    public boolean deletePet(long petId, long userId) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("pets", "id = ? AND user_id = ?", new String[]{String.valueOf(petId), String.valueOf(userId)}) > 0;
    }

    // ===================== Legacy / Helper Methods for UI components =====================

    public List<Category> getAllCategories() {
        List<Category> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id, name FROM categories ORDER BY id ASC", null);
        try {
            while (cursor.moveToNext()) {
                list.add(new Category(cursor.getInt(0), cursor.getString(1)));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (list.isEmpty()) {
            list.add(new Category(1, "Mèo"));
            list.add(new Category(2, "Chó"));
            list.add(new Category(3, "Chim"));
            list.add(new Category(4, "Thỏ"));
            list.add(new Category(5, "Cá"));
        }
        return list;
    }

    public List<Pet> getPetsByUserId(long userId) {
        return getPetsForUser(userId);
    }

    public List<Pet> getPetsByUserId(int userId) {
        return getPetsByUserId((long) userId);
    }

    public long addPet(long userId, String name, int categoryId, String breed, String imageUri) {
        return addPet((int) userId, name, categoryId, breed, imageUri);
    }

    public long addPet(int userId, String name, int categoryId, String breed, String imageUri) {
        long petTypeId = categoryId;
        long petId = insertPet(userId, petTypeId, null, name, null, null, null, null, breed);
        if (petId > 0 && imageUri != null && !imageUri.isEmpty()) {
            savePetAvatarForUser(petId, userId, imageUri);
        }
        return petId;
    }

    public List<GalleryItem> getGalleryByCategory(int categoryId) {
        List<GalleryItem> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id, category_id, image_uri, color_hex, is_download FROM gallery_items WHERE category_id = ?",
                new String[]{String.valueOf(categoryId)});
        try {
            while (cursor.moveToNext()) {
                list.add(new GalleryItem(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getInt(4) == 1
                ));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }

        if (list.isEmpty()) {
            list.add(new GalleryItem(1, categoryId, "", "#E6F4EA", false));
            list.add(new GalleryItem(2, categoryId, "", "#FEF7E0", false));
            list.add(new GalleryItem(3, categoryId, "", "#FCE8E6", true));
        }
        return list;
    }

    public CareGuide getCareGuideByCategory(int categoryId) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id, title, subtitle, content FROM care_guides WHERE category_id = ? LIMIT 1",
                new String[]{String.valueOf(categoryId)});
        try {
            if (cursor.moveToFirst()) {
                return new CareGuide(cursor.getInt(0), categoryId, cursor.getString(1), cursor.getString(2), cursor.getString(3));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return new CareGuide(1, categoryId, "Cách tắm cho pet đúng cách", "Vệ sinh · 3 phút đọc", "Hướng dẫn vệ sinh cơ bản...");
    }

    public ForumPost getForumPostByCategory(int categoryId) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id, group_name, time_ago, content FROM forum_posts WHERE category_id = ? LIMIT 1",
                new String[]{String.valueOf(categoryId)});
        try {
            if (cursor.moveToFirst()) {
                return new ForumPost(cursor.getInt(0), categoryId, cursor.getString(1), cursor.getString(2), cursor.getString(3));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return new ForumPost(1, categoryId, "Hội trao đổi thú cưng", "Mới đây", "Bé nhà bạn hôm nay thế nào?");
    }

    public List<ForumPost> getForumsJoinedByUserId(long userId) {
        return getForumsJoinedByUserId((int) userId);
    }

    public List<ForumPost> getForumsJoinedByUserId(int userId) {
        List<ForumPost> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id, category_id, group_name, time_ago, content FROM forum_posts", null);
        try {
            while (cursor.moveToNext()) {
                list.add(new ForumPost(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)
                ));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (list.isEmpty()) {
            list.add(new ForumPost(1, 1, "Hội yêu mèo", "Tham gia", "Cộng đồng những người yêu thích mèo"));
        }
        return list;
    }

    // ===================== Communities / Group Chat =====================

    public void syncCommunityMemberships(long userId) {
        SQLiteDatabase db = getWritableDatabase();
        List<Long> typeIds = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT DISTINCT pet_type_id FROM pets WHERE user_id = ?",
                new String[]{String.valueOf(userId)});
        try {
            while (cursor.moveToNext()) typeIds.add(cursor.getLong(0));
        } finally {
            cursor.close();
        }

        db.beginTransaction();
        try {
            for (long typeId : typeIds) {
                long communityId = ensureCommunityForPetType(db, typeId);
                if (communityId <= 0) continue;
                ContentValues values = new ContentValues();
                values.put("community_id", communityId);
                values.put("user_id", userId);
                values.put("role", "MEMBER");
                values.put("status", "ACTIVE");
                values.put("joined_at", now());
                db.insertWithOnConflict("community_members", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long ensureCommunityForPetType(SQLiteDatabase db, long petTypeId) {
        Cursor cursor = db.rawQuery("SELECT id FROM communities WHERE pet_type_id = ? ORDER BY id LIMIT 1",
                new String[]{String.valueOf(petTypeId)});
        try {
            if (cursor.moveToFirst()) return cursor.getLong(0);
        } finally {
            cursor.close();
        }

        String typeName = null;
        Cursor typeCursor = db.rawQuery("SELECT name FROM pet_types WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(petTypeId)});
        try {
            if (typeCursor.moveToFirst()) typeName = typeCursor.getString(0);
        } finally {
            typeCursor.close();
        }
        if (typeName == null) return -1L;

        ContentValues values = new ContentValues();
        values.put("pet_type_id", petTypeId);
        values.put("name", "Cộng đồng " + typeName);
        values.put("description", "Nơi những người nuôi " + typeName.toLowerCase(new Locale("vi"))
                + " cùng trò chuyện và chia sẻ kinh nghiệm.");
        values.put("created_at", now());
        return db.insert("communities", null, values);
    }

    public List<Community> getCommunitiesForUser(long userId) {
        syncCommunityMemberships(userId);
        List<Community> items = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT c.id, c.pet_type_id, c.name, c.description " +
                        "FROM communities c " +
                        "INNER JOIN community_members cm ON cm.community_id = c.id " +
                        "WHERE cm.user_id = ? AND cm.status = 'ACTIVE' " +
                        "ORDER BY c.id ASC",
                new String[]{String.valueOf(userId)});
        try {
            while (cursor.moveToNext()) {
                Community com = new Community();
                com.id = cursor.getLong(0);
                com.petTypeId = cursor.isNull(1) ? 0L : cursor.getLong(1);
                com.name = cursor.getString(2);
                com.description = cursor.getString(3);
                items.add(com);
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public Community getCommunity(long communityId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, pet_type_id, name, description FROM communities WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(communityId)});
        try {
            if (!cursor.moveToFirst()) return null;
            Community com = new Community();
            com.id = cursor.getLong(0);
            com.petTypeId = cursor.isNull(1) ? 0L : cursor.getLong(1);
            com.name = cursor.getString(2);
            com.description = cursor.getString(3);
            return com;
        } finally {
            cursor.close();
        }
    }

    public Membership getMembership(long communityId, long userId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT role, status, muted_until " +
                        "FROM community_members WHERE community_id = ? AND user_id = ? LIMIT 1",
                new String[]{String.valueOf(communityId), String.valueOf(userId)});
        try {
            if (!cursor.moveToFirst()) return null;
            return new Membership(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
            );
        } finally {
            cursor.close();
        }
    }

    public List<Message> getRecentMessages(long communityId, int limit) {
        List<Message> items = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT m.id, m.sender_id, m.content, m.created_at, " +
                        "u.username, u.display_name " +
                        "FROM messages m INNER JOIN users u ON u.id = m.sender_id " +
                        "WHERE m.community_id = ? AND m.is_deleted = 0 " +
                        "ORDER BY m.id DESC LIMIT ?",
                new String[]{String.valueOf(communityId), String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                Message msg = new Message();
                msg.id = cursor.getLong(0);
                msg.senderId = cursor.getLong(1);
                msg.content = cursor.getString(2);
                msg.createdAt = cursor.getString(3);
                String uName = cursor.getString(4);
                String dName = cursor.getString(5);
                msg.senderName = dName != null && !dName.trim().isEmpty() ? dName : uName;
                items.add(msg);
            }
        } finally {
            cursor.close();
        }
        Collections.reverse(items);
        return items;
    }

    public long insertMessage(long communityId, long senderId, String content) {
        Message msg = insertMessage(communityId, senderId, content, null);
        return msg != null ? msg.id : -1L;
    }

    public Message insertMessage(long communityId, long senderId, String content, String imageUri) {
        if ((content == null || content.trim().isEmpty()) && (imageUri == null || imageUri.trim().isEmpty())) {
            return null;
        }
        Membership m = getMembership(communityId, senderId);
        if (m == null || !"ACTIVE".equals(m.status)) return null;

        String now = now();
        ContentValues values = new ContentValues();
        values.put("community_id", communityId);
        values.put("sender_id", senderId);
        putNullable(values, "content", content);
        putNullable(values, "image_uri", imageUri);
        values.put("message_type", imageUri != null && !imageUri.trim().isEmpty() ? "IMAGE" : "TEXT");
        values.put("created_at", now);

        SQLiteDatabase db = getWritableDatabase();
        long id = db.insert("messages", null, values);
        if (id <= 0) return null;

        Message msg = new Message();
        msg.id = id;
        msg.senderId = senderId;
        msg.content = content;
        msg.createdAt = now;
        User sender = getUser(senderId);
        msg.senderName = sender != null ? sender.getShownName() : "Thành viên";
        return msg;
    }

    public boolean reportMessage(long messageId, long reporterId, String reason, String detail) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("reporter_id", reporterId);
        values.put("reason", reason);
        putNullable(values, "detail", detail);
        values.put("created_at", now());
        try {
            return db.insert("message_reports", null, values) > 0;
        } catch (SQLiteException ex) {
            return false;
        }
    }

    public boolean deleteMessage(long messageId, long userId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_deleted", 1);
        values.put("deleted_at", now());
        values.put("deleted_by", userId);
        return db.update("messages", values, "id = ?", new String[]{String.valueOf(messageId)}) > 0;
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void putNullable(ContentValues values, String key, String val) {
        if (val == null || val.trim().isEmpty()) values.putNull(key);
        else values.put(key, val.trim());
    }
}