package com.example.apppet.net;

import com.example.apppet.BuildConfig;

/** Cấu hình kết nối, nạp từ local.properties lúc build (xem local.properties.example). */
public final class Config {
    public static final String SUPABASE_URL = trimSlash(BuildConfig.SUPABASE_URL);
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    public static final String IMAGEKIT_ENDPOINT = trimSlash(BuildConfig.IMAGEKIT_ENDPOINT);

    /**
     * true: cho đăng nhập bằng tên đăng nhập (app gọi hàm login_email để đổi sang email). Đổi lại thành false
     * nếu không muốn ai biết username cũng lấy được email - khi đó chỉ đăng nhập được bằng email.
     */
    public static final boolean ALLOW_USERNAME_LOGIN = true;

    private Config() { }

    public static boolean isConfigured() {
        return !SUPABASE_URL.isEmpty() && !SUPABASE_ANON_KEY.isEmpty();
    }

    private static String trimSlash(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
