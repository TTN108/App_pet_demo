package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.SessionManager;
import com.example.apppet.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tài khoản và hồ sơ người dùng (Supabase Auth + bảng profiles). Gọi từ luồng nền. */
public final class UserRepository {
    public static final class SignUpResult {
        public final boolean signedIn;
        public final String message;

        SignUpResult(boolean signedIn, String message) {
            this.signedIn = signedIn;
            this.message = message;
        }
    }

    private final Context app;
    private final SupabaseClient api;

    public UserRepository(Context context) {
        this.app = context.getApplicationContext();
        this.api = SupabaseClient.get(app);
    }

    public boolean usernameAvailable(String username) {
        Object r = api.rpc("username_available", Json.obj("p_username", username));
        return Boolean.TRUE.equals(r);
    }

    public SignUpResult signUp(String username, String email, String password, String displayName) {
        String cleanUser = username.trim().toLowerCase(Locale.ROOT);
        JSONObject body = Json.obj("email", email.trim().toLowerCase(Locale.ROOT), "password", password,
                "data", Json.obj("username", cleanUser,
                        "display_name", displayName == null || displayName.trim().isEmpty() ? cleanUser : displayName.trim()));
        JSONObject res = api.authPost("/auth/v1/signup", body);
        if (res.has("access_token")) {                         // dự án tắt xác nhận email: có phiên ngay
            SessionManager.saveSession(app, SupabaseClient.sessionFrom(res), email.trim().toLowerCase(Locale.ROOT));
            return new SignUpResult(true, null);
        }
        return new SignUpResult(false, "Đăng ký thành công. Hãy mở email để xác nhận tài khoản rồi đăng nhập.");
    }

    /** Đăng nhập bằng email hoặc (nếu Config.ALLOW_USERNAME_LOGIN) tên đăng nhập. */
    public User signIn(String login, String password) {
        String email = login.trim();
        if (!email.contains("@")) {
            if (!Config.ALLOW_USERNAME_LOGIN) throw invalidLogin();
            Object r = api.rpc("login_email", Json.obj("p_username", email));
            if (!(r instanceof String) || ((String) r).isEmpty()) throw invalidLogin();
            email = (String) r;
        }
        JSONObject res = api.authPost("/auth/v1/token?grant_type=password",
                Json.obj("email", email.toLowerCase(Locale.ROOT), "password", password));
        AuthSession session = SupabaseClient.sessionFrom(res);
        JSONObject user = res.optJSONObject("user");
        SessionManager.saveSession(app, session, user != null ? user.optString("email", email) : email);
        User profile = getProfile(session.userId);
        if (profile != null) SessionManager.cacheProfile(app, profile.displayName, profile.avatarUri);
        return profile;
    }

    public void signOut() {
        api.signOutRemote();
        SessionManager.clear(app);
    }

    public User getProfile(String userId) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "id,username,display_name,avatar_url,role,created_at");
        q.put("id", "eq." + userId);
        JSONArray rows = api.select("profiles", q);
        if (rows.length() == 0) return null;
        JSONObject o = rows.optJSONObject(0);
        String email = userId.equals(SessionManager.getUserId(app)) ? SessionManager.getEmail(app) : null;
        User u = new User(Json.str(o, "id"), Json.str(o, "username"), email, Json.str(o, "display_name"),
                o.optString("role", "USER"));
        u.avatarUri = Json.str(o, "avatar_url");
        u.createdAt = TimeUtil.isoToLocal(Json.str(o, "created_at"));
        return u;
    }

    /** Tải ảnh JPEG lên Storage rồi lưu URL vào hồ sơ. @return URL công khai của ảnh. */
    public String changeAvatar(String userId, File jpeg) {
        String url = api.uploadAvatarImage(userId, jpeg);
        Map<String, String> filter = new HashMap<>();
        filter.put("id", "eq." + userId);
        JSONArray updated = api.update("profiles", filter, Json.obj("avatar_url", url));
        if (updated.length() == 0) throw new ApiException(403, "forbidden", "Không cập nhật được ảnh đại diện.");
        SessionManager.cacheProfile(app, SessionManager.getDisplayName(app), url);
        return url;
    }

    private static ApiException invalidLogin() {
        return new ApiException(400, "invalid_credentials", "Invalid login credentials");
    }
}
