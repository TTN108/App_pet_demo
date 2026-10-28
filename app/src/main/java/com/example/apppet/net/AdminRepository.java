package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.AdminItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hàng chờ duyệt của quản trị viên. Server kiểm tra quyền ADMIN ở từng hàm/chính sách, app chỉ là giao diện. */
public final class AdminRepository {
    private static final int LIMIT = 50;
    private final SupabaseClient api;
    private final CatalogDatabase catalog;

    public AdminRepository(Context context) {
        this.api = SupabaseClient.get(context);
        this.catalog = CatalogDatabase.getInstance(context);
    }

    public List<AdminItem> pendingImages() {
        Map<String, String> q = new HashMap<>();
        q.put("select", "id,pet_type_id,breed_id,image_path,origin,label_changed,title,created_at");
        q.put("status", "eq.PENDING");
        q.put("order", "id.asc");
        q.put("limit", String.valueOf(LIMIT));
        JSONArray rows = api.select("gallery_images", q);
        List<AdminItem> items = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            AdminItem it = new AdminItem(AdminItem.TYPE_IMAGE, o.optLong("id"));
            Long typeId = Json.lng(o, "pet_type_id");
            String breed = catalog.breedDisplayName(Json.lng(o, "breed_id"));
            it.title = (typeId != null ? catalog.petTypeName(typeId) : "?") + " · " + (breed != null ? breed : "?");
            StringBuilder body = new StringBuilder("camera".equalsIgnoreCase(o.optString("origin")) ? "Từ camera" : "Tải lên từ thư viện");
            if (!o.isNull("label_changed") && o.optBoolean("label_changed")) {
                body.append("\nNgười dùng ĐÃ ĐỔI giống so với dự đoán của model: nên xem kỹ nhãn.");
            }
            body.append("\nGửi lúc ").append(TimeUtil.isoToLocal(Json.str(o, "created_at")));
            it.body = body.toString();
            it.imageUrl = ImageKitUrls.thumb(Json.str(o, "image_path"), 600);
            items.add(it);
        }
        return items;
    }

    public boolean moderateImage(long id, boolean approve, String reason) {
        return Boolean.TRUE.equals(api.rpc("moderate_gallery_image",
                Json.obj("p_id", id, "p_approve", approve, "p_reason", reason)));
    }

    public List<AdminItem> pendingWiki() {
        Map<String, String> q = new HashMap<>();
        q.put("select", "id,pet_type_id,category_id,title,content,created_at,profiles!author_id(display_name)");
        q.put("status", "eq.PENDING");
        q.put("order", "id.asc");
        q.put("limit", String.valueOf(LIMIT));
        JSONArray rows = api.select("community_wiki", q);
        List<AdminItem> items = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            AdminItem it = new AdminItem(AdminItem.TYPE_WIKI, o.optLong("id"));
            Long typeId = Json.lng(o, "pet_type_id");
            String category = catalog.wikiCategoryName(Json.lng(o, "category_id"));
            JSONObject author = o.optJSONObject("profiles");
            it.title = Json.str(o, "title");
            it.body = (typeId != null ? catalog.petTypeName(typeId) : "?") + " · " + (category != null ? category : "?")
                    + " · đề xuất bởi " + (author != null ? author.optString("display_name", "?") : "?")
                    + "\n\n" + shorten(Json.str(o, "content"), 700);
            items.add(it);
        }
        return items;
    }

    public boolean moderateWiki(long id, boolean approve, String reason) {
        return Boolean.TRUE.equals(api.rpc("moderate_wiki", Json.obj("p_id", id, "p_approve", approve, "p_reason", reason)));
    }

    public List<AdminItem> reports() {
        Object r = api.rpc("admin_reports", null);
        List<AdminItem> items = new ArrayList<>();
        if (!(r instanceof JSONArray)) return items;
        JSONArray rows = (JSONArray) r;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            AdminItem it = new AdminItem(AdminItem.TYPE_REPORT, o.optLong("report_id"));
            it.title = "Báo cáo: " + reasonLabel(o.optString("reason"));
            it.body = "\"" + shorten(Json.str(o, "content"), 400) + "\"\nNgười gửi: " + Json.str(o, "sender_name")
                    + " · Người báo cáo: " + Json.str(o, "reporter_name");
            items.add(it);
        }
        return items;
    }

    /** violation = true: xác nhận vi phạm và xóa tin nhắn; false: bỏ qua báo cáo. */
    public boolean resolveReport(long reportId, boolean violation) {
        return Boolean.TRUE.equals(api.rpc("resolve_report", Json.obj("p_report_id", reportId, "p_violation", violation)));
    }

    private static String reasonLabel(String code) {
        switch (code) {
            case "SPAM": return "spam";
            case "HARASSMENT": return "ngôn từ xúc phạm";
            case "INAPPROPRIATE": return "nội dung không phù hợp";
            case "PRIVACY": return "lộ thông tin cá nhân";
            default: return "lý do khác";
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }
}
