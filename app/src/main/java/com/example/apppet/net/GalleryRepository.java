package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.GalleryImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bộ sưu tập ảnh: thông tin ảnh ở Supabase (gallery_images), file ảnh ở Supabase Storage. Phân trang theo khóa (id), gọi từ luồng nền. */
public final class GalleryRepository {
    private static final String COLUMNS = "id,pet_type_id,breed_id,source_type,image_path,thumb_path,width,height,title,description,source_url";

    private final SupabaseClient api;
    private final CatalogDatabase catalog;

    public GalleryRepository(Context context) {
        this.api = SupabaseClient.get(context);
        this.catalog = CatalogDatabase.getInstance(context);
    }

    /** Một trang ảnh đã duyệt có id > afterId (afterId = 0 cho trang đầu). Lọc theo loài/giống nếu khác null. */
    public List<GalleryImage> page(Long petTypeId, Long breedId, long afterId, int limit) {
        Map<String, String> q = baseQuery(limit);
        if (petTypeId != null) q.put("pet_type_id", "eq." + petTypeId);
        if (breedId != null) q.put("breed_id", "eq." + breedId);
        if (afterId > 0) q.put("id", "gt." + afterId);
        return parse(api.select("gallery_images", q));
    }

    /** Ảnh xem trước cho thú cưng: ưu tiên đúng giống, bù thêm ảnh cùng loài cho đủ limit. */
    public List<GalleryImage> previewForPet(long petTypeId, Long breedId, int limit) {
        List<GalleryImage> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (breedId != null) {
            for (GalleryImage g : page(petTypeId, breedId, 0, limit)) if (seen.add(g.id)) out.add(g);
        }
        if (out.size() < limit) {
            for (GalleryImage g : page(petTypeId, null, 0, limit)) {
                if (out.size() >= limit) break;
                if (seen.add(g.id)) out.add(g);
            }
        }
        return out;
    }

    private static Map<String, String> baseQuery(int limit) {
        Map<String, String> q = new HashMap<>();
        q.put("select", COLUMNS);
        q.put("status", "eq.APPROVED");
        q.put("order", "id.asc");
        q.put("limit", String.valueOf(limit));
        return q;
    }

    private List<GalleryImage> parse(JSONArray rows) {
        List<GalleryImage> items = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            GalleryImage g = new GalleryImage();
            g.id = o.optLong("id");
            g.petTypeId = Json.lng(o, "pet_type_id");
            g.breedId = Json.lng(o, "breed_id");
            g.sourceType = Json.str(o, "source_type");
            g.imagePath = Json.str(o, "image_path");
            g.thumbPath = Json.str(o, "thumb_path");
            g.width = o.optInt("width", 0);
            g.height = o.optInt("height", 0);
            g.title = Json.str(o, "title");
            g.description = Json.str(o, "description");
            g.sourceUrl = Json.str(o, "source_url");
            g.petTypeName = g.petTypeId != null ? catalog.petTypeName(g.petTypeId) : null;
            g.breedName = g.title;
            items.add(g);
        }
        return items;
    }
}
