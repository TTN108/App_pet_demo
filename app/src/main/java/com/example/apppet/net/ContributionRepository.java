package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.WikiArticle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Đóng góp của người dùng: ảnh cho bộ sưu tập và bài wiki đề xuất từ chat. Gọi từ luồng nền. */
public final class ContributionRepository {
    private final SupabaseClient api;
    private final CatalogDatabase catalog;

    public ContributionRepository(Context context) {
        this.api = SupabaseClient.get(context);
        this.catalog = CatalogDatabase.getInstance(context);
    }

    /**
     * Tải ảnh lên bucket contrib rồi tạo dòng gallery_images ở trạng thái PENDING (hàm server kiểm tra URL, giống,
     * giới hạn 20 ảnh chờ duyệt). origin: "UPLOAD" hoặc "CAMERA".
     */
    public long contributeImage(String userId, long petTypeId, long breedId, File jpeg, String origin,
                                long recognitionId, String title) {
        String url = api.uploadContributionImage(userId, jpeg);
        Object id = api.rpc("contribute_gallery_image", Json.obj(
                "p_pet_type_id", petTypeId, "p_breed_id", breedId, "p_image_url", url, "p_origin", origin,
                "p_recognition_id", recognitionId > 0 ? recognitionId : null, "p_title", title));
        return id instanceof Number ? ((Number) id).longValue() : -1L;
    }

    /** Gửi đề xuất đưa nội dung một tin nhắn thành bài wiki; admin duyệt mới hiển thị. */
    public long proposeWiki(long messageId, String title, long categoryId, String content, Long breedId, boolean showAuthor) {
        Object id = api.rpc("propose_wiki_from_message", Json.obj(
                "p_message_id", messageId, "p_title", title, "p_category_id", categoryId, "p_content", content,
                "p_breed_id", breedId, "p_show_author", showAuthor));
        return id instanceof Number ? ((Number) id).longValue() : -1L;
    }

    /** Bài wiki cộng đồng đã được duyệt (tên tác giả chỉ hiện nếu người đề xuất đồng ý). */
    public List<WikiArticle> communityWiki(Long petTypeId, Long breedId, String query, int limit) {
        Object r = api.rpc("list_community_wiki", Json.obj("p_pet_type_id", petTypeId, "p_breed_id", breedId,
                "p_query", query == null || query.trim().isEmpty() ? null : query.trim(), "p_limit", limit));
        List<WikiArticle> items = new ArrayList<>();
        if (r instanceof JSONArray) {
            JSONArray rows = (JSONArray) r;
            for (int i = 0; i < rows.length(); i++) items.add(toArticle(rows.optJSONObject(i)));
        }
        return items;
    }

    public WikiArticle communityWikiArticle(long id) {
        Object r = api.rpc("get_community_wiki", Json.obj("p_id", id));
        if (r instanceof JSONArray && ((JSONArray) r).length() > 0) return toArticle(((JSONArray) r).optJSONObject(0));
        return null;
    }

    private WikiArticle toArticle(JSONObject o) {
        WikiArticle w = new WikiArticle();
        w.id = o.optLong("id");
        w.petTypeId = Json.lng(o, "pet_type_id");
        w.breedId = Json.lng(o, "breed_id");
        w.categoryId = Json.lng(o, "category_id");
        w.categoryName = catalog.wikiCategoryName(w.categoryId);
        w.petTypeName = w.petTypeId != null ? catalog.petTypeName(w.petTypeId) : null;
        w.breedName = catalog.breedDisplayName(w.breedId);
        w.title = Json.str(o, "title");
        w.summary = Json.str(o, "summary");
        w.content = Json.str(o, "content");
        w.sourceType = "COMMUNITY";
        String author = Json.str(o, "author_name");
        w.sourceName = author != null ? "Cộng đồng · " + author : "Cộng đồng";
        w.crawledAt = TimeUtil.isoToLocal(Json.str(o, "created_at"));
        w.community = true;
        return w;
    }
}
