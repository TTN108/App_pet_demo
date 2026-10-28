package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.Community;
import com.example.apppet.model.Membership;
import com.example.apppet.model.Message;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Cộng đồng và chat nhóm. Mọi thao tác ghi đi qua hàm SECURITY DEFINER trên server (kiểm tra thành viên, mute,
 * quyền xóa) nên app không thể "tự cho mình quyền". Gọi từ luồng nền.
 */
public final class ChatRepository {
    /** Một lượt đồng bộ tin nhắn: các tin mới/đổi và mốc cursor để lần sau chỉ hỏi phần thay đổi. */
    public static final class Page {
        public final List<Message> items;
        public final String cursor;

        Page(List<Message> items, String cursor) {
            this.items = items;
            this.cursor = cursor;
        }
    }

    private final SupabaseClient api;
    private final CatalogDatabase catalog;

    public ChatRepository(Context context) {
        this.api = SupabaseClient.get(context);
        this.catalog = CatalogDatabase.getInstance(context);
    }

    /** Cộng đồng của các loài người dùng đang nuôi (server tự cho vào nhóm). */
    public List<Community> myCommunities() {
        Object r = api.rpc("my_communities", null);
        List<Community> items = new ArrayList<>();
        if (!(r instanceof JSONArray)) return items;
        JSONArray rows = (JSONArray) r;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i);
            Community c = new Community();
            c.id = o.optLong("id");
            c.petTypeId = o.optLong("pet_type_id", 0L);
            c.name = Json.str(o, "name");
            c.description = Json.str(o, "description");
            c.petTypeName = c.petTypeId > 0 ? catalog.petTypeName(c.petTypeId) : null;
            c.memberCount = o.optInt("member_count", 0);
            c.lastMessage = Json.str(o, "last_message");
            c.lastMessageAt = TimeUtil.isoToLocal(Json.str(o, "last_message_at"));
            items.add(c);
        }
        return items;
    }

    public Community getCommunity(long communityId) {
        for (Community c : myCommunities()) if (c.id == communityId) return c;
        return null;
    }

    /** null nếu không phải thành viên. */
    public Membership myMembership(long communityId) {
        Object r = api.rpc("my_membership", Json.obj("p_community_id", communityId));
        if (!(r instanceof JSONArray) || ((JSONArray) r).length() == 0) return null;
        JSONObject o = ((JSONArray) r).optJSONObject(0);
        return new Membership(o.optString("role", "MEMBER"), o.optString("status", "ACTIVE"),
                Json.str(o, "muted_until"), o.optBoolean("can_send", false));
    }

    /**
     * sinceCursor = null: tải 100 tin gần nhất. Ngược lại chỉ tải tin mới hoặc vừa bị xóa kể từ mốc đó
     * (lùi 2 giây để không sót tin commit trễ; app khử trùng theo id).
     */
    public Page getMessages(long communityId, String sinceCursor, int limit) {
        String since = sinceCursor == null ? null : TimeUtil.minusSeconds(sinceCursor, 2);
        Object r = api.rpc("get_messages", Json.obj("p_community_id", communityId, "p_since", since, "p_limit", limit));
        List<Message> items = new ArrayList<>();
        String cursor = sinceCursor;
        if (r instanceof JSONArray) {
            JSONArray rows = (JSONArray) r;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.optJSONObject(i);
                Message m = new Message();
                m.id = o.optLong("id");
                m.senderId = Json.str(o, "sender_id");
                m.senderName = Json.str(o, "sender_name");
                m.deleted = o.optBoolean("is_deleted", false);
                m.content = m.deleted ? null : Json.str(o, "content");
                m.createdAt = TimeUtil.isoToLocal(Json.str(o, "created_at"));
                items.add(m);
                cursor = TimeUtil.later(cursor, Json.str(o, "changed_at"));
            }
        }
        return new Page(items, cursor);
    }

    public long sendMessage(long communityId, String content) {
        Object r = api.rpc("send_message", Json.obj("p_community_id", communityId, "p_content", content));
        return r instanceof Number ? ((Number) r).longValue() : -1L;
    }

    public boolean deleteMessage(long messageId) {
        return Boolean.TRUE.equals(api.rpc("delete_message", Json.obj("p_message_id", messageId)));
    }

    /** reasonCode: SPAM, INAPPROPRIATE, HARASSMENT, PRIVACY hoặc OTHER. */
    public boolean reportMessage(long messageId, String reasonCode, String detail) {
        return Boolean.TRUE.equals(api.rpc("report_message",
                Json.obj("p_message_id", messageId, "p_reason", reasonCode, "p_detail", detail)));
    }
}
