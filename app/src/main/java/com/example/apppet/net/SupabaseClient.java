package com.example.apppet.net;

import android.content.Context;

import com.example.apppet.data.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Máy khách HTTP mỏng cho Supabase (Auth, PostgREST, Storage). Tự thêm apikey + Bearer token, tự làm mới token
 * khi sắp hết hạn hoặc gặp 401. TẤT CẢ hàm ở đây là đồng bộ và gọi mạng: chỉ gọi từ luồng nền (AppExecutors).
 */
public final class SupabaseClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_UPLOAD_BYTES = 2_000_000;      // khớp file_size_limit của bucket avatars
    private static final int MAX_CONTRIB_BYTES = 3_000_000;     // khớp file_size_limit của bucket contrib

    private static SupabaseClient instance;
    private final Context app;
    private final OkHttpClient http;
    private final Object refreshLock = new Object();

    public static synchronized SupabaseClient get(Context context) {
        if (instance == null) instance = new SupabaseClient(context.getApplicationContext());
        return instance;
    }

    private SupabaseClient(Context app) {
        this.app = app;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ===================== Auth =====================

    /** Gọi endpoint /auth/v1/... không kèm phiên đăng nhập (signup, đăng nhập). */
    public JSONObject authPost(String pathWithQuery, JSONObject body) {
        String text = call("POST", pathWithQuery, null, RequestBody.create(body.toString(), JSON), null, false);
        Object v = parse(text);
        if (!(v instanceof JSONObject)) throw new ApiException(500, "bad_response", "Phản hồi đăng nhập không hợp lệ.");
        return (JSONObject) v;
    }

    public static AuthSession sessionFrom(JSONObject res) {
        try {
            long exp = res.has("expires_at") && !res.isNull("expires_at")
                    ? res.getLong("expires_at")
                    : System.currentTimeMillis() / 1000L + res.optLong("expires_in", 3600L);
            return new AuthSession(res.getJSONObject("user").getString("id"), res.getString("access_token"),
                    res.optString("refresh_token", ""), exp);
        } catch (JSONException ex) {
            throw new ApiException(500, "bad_response", "Phản hồi đăng nhập thiếu dữ liệu.");
        }
    }

    public void signOutRemote() {
        try {
            call("POST", "/auth/v1/logout", null, RequestBody.create(new byte[0], null), null, true);
        } catch (RuntimeException ignored) {
            // đăng xuất cục bộ vẫn tiếp tục dù server không phản hồi
        }
    }

    /** Access token còn hạn (làm mới nếu cần) - dùng cho Realtime. */
    public String freshAccessToken() {
        return bearer();
    }

    private String bearer() {
        AuthSession s = SessionManager.loadSession(app);
        if (s == null) return Config.SUPABASE_ANON_KEY;
        if (s.expiresWithin(60)) s = refresh(false);
        return s.accessToken;
    }

    private AuthSession refresh(boolean force) {
        synchronized (refreshLock) {
            AuthSession current = SessionManager.loadSession(app);
            if (current == null) throw new ApiException(401, "session_expired", "Chưa đăng nhập");
            if (!force && !current.expiresWithin(60)) return current;      // luồng khác vừa làm mới xong
            try {
                JSONObject res = authPost("/auth/v1/token?grant_type=refresh_token",
                        Json.obj("refresh_token", current.refreshToken));
                AuthSession fresh = sessionFrom(res);
                SessionManager.saveSession(app, fresh, null);
                return fresh;
            } catch (ApiException ex) {
                if (ex.status == 400 || ex.status == 401 || ex.status == 403) {
                    SessionManager.clear(app);                              // refresh token không còn dùng được
                    throw new ApiException(401, "session_expired", "Phiên đăng nhập đã hết hạn");
                }
                throw ex;                                                   // lỗi mạng/máy chủ: giữ phiên
            }
        }
    }

    // ===================== PostgREST =====================

    public JSONArray select(String table, Map<String, String> query) {
        return asArray(parse(call("GET", "/rest/v1/" + table, query, null, null, true)));
    }

    /** INSERT, trả về các dòng vừa tạo. */
    public JSONArray insert(String table, JSONObject row) {
        return asArray(parse(call("POST", "/rest/v1/" + table, null, RequestBody.create(row.toString(), JSON),
                "return=representation", true)));
    }

    /** PATCH theo bộ lọc; mảng rỗng nghĩa là không dòng nào khớp (hoặc không có quyền - RLS). */
    public JSONArray update(String table, Map<String, String> filter, JSONObject patch) {
        return asArray(parse(call("PATCH", "/rest/v1/" + table, filter, RequestBody.create(patch.toString(), JSON),
                "return=representation", true)));
    }

    public JSONArray delete(String table, Map<String, String> filter) {
        return asArray(parse(call("DELETE", "/rest/v1/" + table, filter, null, "return=representation", true)));
    }

    /** Gọi hàm Postgres. Trả về JSONArray/JSONObject/String/Boolean/Number hoặc JSONObject.NULL. */
    public Object rpc(String function, JSONObject args) {
        JSONObject body = args == null ? new JSONObject() : args;
        return parse(call("POST", "/rest/v1/rpc/" + function, null, RequestBody.create(body.toString(), JSON), null, true));
    }

    // ===================== Storage =====================

    /** Ảnh đại diện/ảnh thú cưng: bucket public "avatars". @return URL công khai. */
    public String uploadAvatarImage(String userId, File jpeg) {
        return uploadImage("avatars", userId, jpeg);
    }

    /** Ảnh đóng góp cho bộ sưu tập: bucket "contrib" (URL không đoán được, chỉ hiện công khai sau khi admin duyệt). */
    public String uploadContributionImage(String userId, File jpeg) {
        return uploadImage("contrib", userId, jpeg);
    }

    /** Tải JPEG lên thư mục có tên = id người dùng (đúng policy RLS của bucket). */
    private String uploadImage(String bucket, String userId, File jpeg) {
        byte[] data = readAll(jpeg);
        int limit = "contrib".equals(bucket) ? MAX_CONTRIB_BYTES : MAX_UPLOAD_BYTES;
        if (data.length > limit) throw new ApiException(413, "too_large", "Ảnh quá lớn (tối đa " + (limit / 1_000_000) + " MB).");
        String objectPath = userId + "/" + UUID.randomUUID() + ".jpg";
        call("POST", "/storage/v1/object/" + bucket + "/" + objectPath, null,
                RequestBody.create(data, MediaType.get("image/jpeg")), null, true);
        return Config.SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private static byte[] readAll(File f) {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(0, "io", "Không đọc được tệp ảnh.");
        }
    }

    // ===================== lõi =====================

    private String call(String method, String path, Map<String, String> query, RequestBody body,
                        String prefer, boolean useSession) {
        if (!Config.isConfigured()) {
            throw new ApiException(0, "not_configured", "Chưa cấu hình Supabase: điền local.properties rồi build lại.");
        }
        HttpUrl base = HttpUrl.parse(Config.SUPABASE_URL + path);
        if (base == null) throw new ApiException(0, "bad_url", "SUPABASE_URL không hợp lệ.");
        HttpUrl.Builder url = base.newBuilder();
        if (query != null) {
            for (Map.Entry<String, String> e : query.entrySet()) url.addQueryParameter(e.getKey(), e.getValue());
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = useSession ? bearer() : Config.SUPABASE_ANON_KEY;
            Request.Builder rb = new Request.Builder().url(url.build())
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + token)
                    .method(method, body);
            if (prefer != null) rb.header("Prefer", prefer);
            try (Response r = http.newCall(rb.build()).execute()) {
                String text = r.body() != null ? r.body().string() : "";
                if (r.isSuccessful()) return text;
                if (r.code() == 401 && useSession && attempt == 0 && SessionManager.isLoggedIn(app)) {
                    refresh(true);                                          // token bị từ chối: làm mới rồi thử lại 1 lần
                    continue;
                }
                throw parseError(r.code(), text);
            } catch (IOException ex) {
                throw new ApiException(0, "network", ex.getMessage());
            }
        }
        throw new ApiException(401, "session_expired", "Phiên đăng nhập đã hết hạn");
    }

    private static ApiException parseError(int status, String text) {
        String message = "", code = "";
        try {
            JSONObject o = new JSONObject(text);
            for (String k : new String[]{"message", "msg", "error_description", "error"}) {
                if (message.isEmpty() && o.optString(k, "").length() > 0) message = o.optString(k);
            }
            code = o.optString("error_code", "");
            if (code.isEmpty()) code = o.optString("code", "");
        } catch (JSONException ignored) {
            message = text == null ? "" : text;
        }
        return new ApiException(status, code, message);
    }

    private static Object parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new JSONTokener(text).nextValue();
        } catch (JSONException ex) {
            throw new ApiException(500, "bad_response", "Phản hồi máy chủ không hợp lệ.");
        }
    }

    private static JSONArray asArray(Object v) {
        if (v instanceof JSONArray) return (JSONArray) v;
        return new JSONArray();
    }
}
