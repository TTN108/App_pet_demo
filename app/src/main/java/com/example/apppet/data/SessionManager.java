package com.example.apppet.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.apppet.net.AuthSession;

/**
 * Lưu phiên đăng nhập (token Supabase) và cài đặt giao diện trong SharedPreferences riêng của app
 * (allowBackup=false nên không bị sao lưu lên Google). Không gọi mạng/DB nên dùng được ở luồng UI.
 */
public class SessionManager {
    private static final String PREFS = "app_pet_session";
    private static final String KEY_USER_ID = "user_id_v2";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES = "expires_at";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "display_name";
    private static final String KEY_AVATAR = "avatar_url";
    private static final String KEY_BG_COLOR = "bgColor";
    private static final String KEY_CARD_COLOR = "cardColor";

    public static final String DEFAULT_BG_COLOR = "#F4F7FC";
    public static final String DEFAULT_CARD_COLOR = "#FFFFFF";

    private SessionManager() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- phiên đăng nhập ----------------

    public static boolean isLoggedIn(Context c) {
        SharedPreferences p = prefs(c);
        return !p.getString(KEY_USER_ID, "").isEmpty() && !p.getString(KEY_REFRESH, "").isEmpty();
    }

    /** Id người dùng (uuid) hoặc chuỗi rỗng nếu chưa đăng nhập. */
    public static String getUserId(Context c) {
        return prefs(c).getString(KEY_USER_ID, "");
    }

    public static AuthSession loadSession(Context c) {
        SharedPreferences p = prefs(c);
        String uid = p.getString(KEY_USER_ID, "");
        String refresh = p.getString(KEY_REFRESH, "");
        if (uid.isEmpty() || refresh.isEmpty()) return null;
        return new AuthSession(uid, p.getString(KEY_ACCESS, ""), refresh, p.getLong(KEY_EXPIRES, 0L));
    }

    public static void saveSession(Context c, AuthSession s, String email) {
        SharedPreferences.Editor e = prefs(c).edit()
                .putString(KEY_USER_ID, s.userId)
                .putString(KEY_ACCESS, s.accessToken)
                .putString(KEY_REFRESH, s.refreshToken)
                .putLong(KEY_EXPIRES, s.expiresAtSec);
        if (email != null) e.putString(KEY_EMAIL, email);
        e.apply();
    }

    public static void cacheProfile(Context c, String displayName, String avatarUrl) {
        prefs(c).edit().putString(KEY_NAME, displayName == null ? "" : displayName)
                .putString(KEY_AVATAR, avatarUrl == null ? "" : avatarUrl).apply();
    }

    public static String getEmail(Context c) { return prefs(c).getString(KEY_EMAIL, ""); }

    public static String getDisplayName(Context c) { return prefs(c).getString(KEY_NAME, ""); }

    public static String getAvatarUrl(Context c) { return prefs(c).getString(KEY_AVATAR, ""); }

    /** Đăng xuất cục bộ: xóa token và thông tin tài khoản, GIỮ lại màu giao diện đã chọn. */
    public static void clear(Context c) {
        prefs(c).edit().remove(KEY_USER_ID).remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES)
                .remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_AVATAR).apply();
    }

    // ---------------- giao diện ----------------

    public static void setThemeColors(Context c, String bgColor, String cardColor) {
        prefs(c).edit().putString(KEY_BG_COLOR, bgColor).putString(KEY_CARD_COLOR, cardColor).apply();
    }

    public static String getBgColor(Context c) { return prefs(c).getString(KEY_BG_COLOR, DEFAULT_BG_COLOR); }

    public static String getCardColor(Context c) { return prefs(c).getString(KEY_CARD_COLOR, DEFAULT_CARD_COLOR); }
}
