package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.Pet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thú cưng, ảnh thú cưng và kết quả nhận diện trên Supabase. Tên loài/giống lấy từ catalog trong app. Gọi từ luồng nền. */
public final class PetRepository {
    private static final String PET_COLUMNS = "*,pet_images(id,image_url,is_main,created_at)";

    private final SupabaseClient api;
    private final CatalogDatabase catalog;

    public PetRepository(Context context) {
        this.api = SupabaseClient.get(context);
        this.catalog = CatalogDatabase.getInstance(context);
    }

    public List<Pet> listPets() {
        Map<String, String> q = new HashMap<>();
        q.put("select", PET_COLUMNS);
        q.put("order", "updated_at.desc,id.desc");
        JSONArray rows = api.select("pets", q);          // RLS: chỉ trả thú cưng của chính người dùng
        List<Pet> pets = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) pets.add(toPet(rows.optJSONObject(i)));
        return pets;
    }

    /** null nếu không tồn tại hoặc không phải của người dùng (RLS). */
    public Pet getPet(long petId) {
        Map<String, String> q = new HashMap<>();
        q.put("select", PET_COLUMNS);
        q.put("id", "eq." + petId);
        JSONArray rows = api.select("pets", q);
        return rows.length() == 0 ? null : toPet(rows.optJSONObject(0));
    }

    public long insertPet(long petTypeId, Long breedId, String name, String gender, String birthDate,
                          String color, Double weight, String description) {
        JSONArray created = api.insert("pets", body(petTypeId, breedId, name, gender, birthDate, color, weight, description));
        if (created.length() == 0) throw new ApiException(500, "empty", "Không tạo được thú cưng.");
        return created.optJSONObject(0).optLong("id", -1L);
    }

    public boolean updatePet(long petId, long petTypeId, Long breedId, String name, String gender, String birthDate,
                             String color, Double weight, String description) {
        Map<String, String> filter = new HashMap<>();
        filter.put("id", "eq." + petId);
        return api.update("pets", filter, body(petTypeId, breedId, name, gender, birthDate, color, weight, description)).length() > 0;
    }

    public boolean deletePet(long petId) {
        Map<String, String> filter = new HashMap<>();
        filter.put("id", "eq." + petId);
        return api.delete("pets", filter).length() > 0;     // ảnh liên quan tự xóa (ON DELETE CASCADE)
    }

    /** Tải ảnh lên Storage rồi đặt làm ảnh chính của thú cưng. @return {id pet_images, url}. */
    public Object[] setMainImage(String userId, long petId, File jpeg) {
        String url = api.uploadAvatarImage(userId, jpeg);
        Object id = api.rpc("set_pet_main_image", Json.obj("p_pet_id", petId, "p_image_url", url));
        return new Object[]{id instanceof Number ? ((Number) id).longValue() : -1L, url};
    }

    /** Lưu một kết quả nhận diện (chưa gắn ảnh). @return id dòng recognition_results. */
    public long insertRecognition(Long petTypeId, Long breedId, double confidence, String model, String rawLabel,
                                  String resultJson) {
        Object json;
        try {
            json = resultJson == null ? JSONObject.NULL : new JSONArray(resultJson);
        } catch (JSONException ex) {
            json = resultJson;
        }
        JSONArray created = api.insert("recognition_results", Json.obj(
                "detected_pet_type_id", petTypeId,
                "detected_breed_id", petTypeId == null ? null : breedId,
                "confidence", confidence, "model_name", model, "raw_label", rawLabel, "result_json", json));
        return created.length() == 0 ? -1L : created.optJSONObject(0).optLong("id", -1L);
    }

    public void linkRecognition(long recognitionId, long petImageId) {
        if (recognitionId <= 0 || petImageId <= 0) return;
        api.rpc("link_recognition", Json.obj("p_recognition_id", recognitionId, "p_pet_image_id", petImageId));
    }

    private static JSONObject body(long petTypeId, Long breedId, String name, String gender, String birthDate,
                                   String color, Double weight, String description) {
        return Json.obj("pet_type_id", petTypeId, "breed_id", breedId, "name", name.trim(),
                "gender", blank(gender), "birth_date", blank(birthDate), "color", blank(color),
                "weight", weight, "description", blank(description));
    }

    private static String blank(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private Pet toPet(JSONObject o) {
        Pet p = new Pet();
        p.id = o.optLong("id");
        p.userId = Json.str(o, "user_id");
        p.petTypeId = o.optLong("pet_type_id");
        p.breedId = Json.lng(o, "breed_id");
        p.name = Json.str(o, "name");
        p.gender = Json.str(o, "gender");
        p.birthDate = Json.str(o, "birth_date");
        p.color = Json.str(o, "color");
        p.weight = o.isNull("weight") ? null : o.optDouble("weight");
        p.description = Json.str(o, "description");
        p.createdAt = TimeUtil.isoToLocal(Json.str(o, "created_at"));
        p.updatedAt = TimeUtil.isoToLocal(Json.str(o, "updated_at"));
        p.petTypeName = catalog.petTypeName(p.petTypeId);
        p.breedName = catalog.breedDisplayName(p.breedId);
        JSONArray images = o.optJSONArray("pet_images");
        long bestId = -1;
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.optJSONObject(i);
                long id = img.optLong("id");
                boolean main = img.optBoolean("is_main", false);
                if (main || (p.avatarUri == null && id > bestId)) {
                    p.avatarUri = Json.str(img, "image_url");
                    bestId = id;
                    if (main) break;
                }
            }
        }
        return p;
    }
}
