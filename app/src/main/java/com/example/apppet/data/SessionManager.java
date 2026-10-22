package com.example.apppet.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.apppet.model.User;

/**
 * Quản lý phiên đăng nhập và cài đặt giao diện trong SharedPreferences.
 * Hỗ trợ cả truy cập Instance và truy cập Static.
 */
public class SessionManager {
    private static final String PREFS = "app_pet_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_AVATAR_URI = "avatarUri";
    private static final String KEY_CREATED_AT = "createdAt";

    private static final String KEY_BG_COLOR = "bgColor";
    private static final String KEY_CARD_COLOR = "cardColor";

    public static final String DEFAULT_BG_COLOR = "#F4F7FC";
    public static final String DEFAULT_CARD_COLOR = "#FFFFFF";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.pref = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.editor = pref.edit();
    }

    // --- Instance Methods ---

    public void createLoginSession(int userId, String username, String email, String displayName, String avatarUri, String createdAt) {
        editor.putLong(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_DISPLAY_NAME, displayName);
        editor.putString(KEY_AVATAR_URI, avatarUri);
        editor.putString(KEY_CREATED_AT, createdAt);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return isLoggedIn(context);
    }

    public int getUserId() {
        return (int) getUserId(context);
    }

    public String getUsername() {
        return pref.getString(KEY_USERNAME, "");
    }

    public String getEmail() {
        return pref.getString(KEY_EMAIL, "");
    }

    public String getDisplayName() {
        return pref.getString(KEY_DISPLAY_NAME, "Người dùng");
    }

    public String getAvatarUri() {
        return pref.getString(KEY_AVATAR_URI, "");
    }

    public String getCreatedAt() {
        return pref.getString(KEY_CREATED_AT, "");
    }

    public void updateAvatarUri(String avatarUri) {
        editor.putString(KEY_AVATAR_URI, avatarUri).apply();
    }

    public void updateDisplayName(String displayName) {
        editor.putString(KEY_DISPLAY_NAME, displayName).apply();
    }

    public void setThemeColors(String bgColor, String cardColor) {
        editor.putString(KEY_BG_COLOR, bgColor);
        editor.putString(KEY_CARD_COLOR, cardColor);
        editor.apply();
    }

    public String getBgColor() {
        return pref.getString(KEY_BG_COLOR, DEFAULT_BG_COLOR);
    }

    public String getCardColor() {
        return pref.getString(KEY_CARD_COLOR, DEFAULT_CARD_COLOR);
    }

    public void logoutUser() {
        clear(context);
    }

    // --- Static Methods ---

    public static long getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public static boolean isLoggedIn(Context context) {
        long id = getUserId(context);
        if (id <= 0) return false;
        User user = new DatabaseHelper(context).getUser(id);
        if (user == null || user.placeholderAccount) {
            clear(context);
            return false;
        }
        return true;
    }

    public static void setUserId(Context context, long userId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_USER_ID, userId).apply();
    }

    public static void setThemeColors(Context context, String bgColor, String cardColor) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BG_COLOR, bgColor)
                .putString(KEY_CARD_COLOR, cardColor)
                .apply();
    }

    public static String getBgColor(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BG_COLOR, DEFAULT_BG_COLOR);
    }

    public static String getCardColor(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CARD_COLOR, DEFAULT_CARD_COLOR);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}