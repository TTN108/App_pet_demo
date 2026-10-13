package com.example.apppet.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lưu id người dùng đang đăng nhập.
 * Hiện tại app chạy demo nên tự dùng tài khoản demo khi chưa có session.
 * Khi làm đăng nhập, gọi setUserId(context, id) sau khi xác thực thành công.
 */
public final class SessionManager {
    private static final String PREFS = "app_pet_session";
    private static final String KEY_USER_ID = "user_id";

    private SessionManager() { }

    public static long getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_USER_ID)) {
            return prefs.getLong(KEY_USER_ID, -1L);
        }
        long demoId = new DatabaseHelper(context).getDemoUserId();
        prefs.edit().putLong(KEY_USER_ID, demoId).apply();
        return demoId;
    }

    public static void setUserId(Context context, long userId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_USER_ID, userId).apply();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_USER_ID).apply();
    }
}
