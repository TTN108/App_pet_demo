package com.example.apppet.net;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Nghe thay đổi bảng messages của một cộng đồng qua Supabase Realtime (giao thức Phoenix 1.0.0, postgres_changes).
 * Chỉ dùng làm "tín hiệu đánh thức": khi có thay đổi sẽ gọi onChange() để app tải phần mới bằng get_messages.
 * Nếu kết nối thất bại, onStatus(false) và app tự quay về polling nên chat vẫn chạy. Callback ở luồng nền của OkHttp.
 */
public final class RealtimeClient {
    public interface Listener {
        void onChange();

        void onStatus(boolean connected);
    }

    private final Context app;
    private final String topic;
    private final long communityId;
    private final Listener listener;
    private final OkHttpClient http = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
    private final AtomicInteger refs = new AtomicInteger(1);

    private volatile boolean running;
    private volatile WebSocket socket;
    private ScheduledExecutorService scheduler;
    private String lastToken;
    private int attempt = 0;

    public RealtimeClient(Context context, long communityId, Listener listener) {
        this.app = context.getApplicationContext();
        this.communityId = communityId;
        this.topic = "realtime:chat-" + communityId;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running || !Config.isConfigured()) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.execute(this::connect);
        scheduler.scheduleWithFixedDelay(this::heartbeat, 25, 25, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        running = false;
        WebSocket s = socket;
        socket = null;
        if (s != null) s.close(1000, "bye");
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
    }

    private void connect() {
        if (!running) return;
        try {
            lastToken = SupabaseClient.get(app).freshAccessToken();
            String ws = Config.SUPABASE_URL.replaceFirst("^http", "ws")
                    + "/realtime/v1/websocket?apikey=" + Config.SUPABASE_ANON_KEY + "&vsn=1.0.0";
            WebSocket old = socket;
            if (old != null) old.cancel();
            socket = http.newWebSocket(new Request.Builder().url(ws).build(), new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket, topic, "phx_join", join());
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handle(webSocket, text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    dropped(webSocket);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    dropped(webSocket);
                }
            });
        } catch (RuntimeException ex) {
            listener.onStatus(false);
            reconnectLater();
        }
    }

    private JSONObject join() {
        JSONObject change = Json.obj("event", "*", "schema", "public", "table", "messages",
                "filter", "community_id=eq." + communityId);
        org.json.JSONArray changes = new org.json.JSONArray().put(change);
        JSONObject config = Json.obj("broadcast", Json.obj("ack", false, "self", false),
                "presence", Json.obj("key", ""), "postgres_changes", changes);
        return Json.obj("config", config, "access_token", lastToken);
    }

    private void handle(WebSocket ws, String text) {
        if (ws != socket) return;
        try {
            JSONObject msg = new JSONObject(text);
            String event = msg.optString("event");
            JSONObject payload = msg.optJSONObject("payload");
            switch (event) {
                case "postgres_changes":
                    listener.onChange();
                    break;
                case "phx_reply":
                    if (topic.equals(msg.optString("topic"))) {
                        boolean ok = payload != null && "ok".equals(payload.optString("status"));
                        if (ok) attempt = 0;
                        listener.onStatus(ok);
                    }
                    break;
                case "system":
                    if (payload != null && "error".equals(payload.optString("status"))) listener.onStatus(false);
                    break;
                case "phx_error":
                case "phx_close":
                    dropped(ws);
                    break;
                default:
                    break;
            }
        } catch (JSONException ignored) {
            // khung không phải JSON: bỏ qua
        }
    }

    private void dropped(WebSocket ws) {
        if (ws != socket || !running) return;
        listener.onStatus(false);
        reconnectLater();
    }

    private synchronized void reconnectLater() {
        if (!running || scheduler == null) return;
        long delay = Math.min(30, 1L << Math.min(attempt++, 5));
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // đã stop()
        }
    }

    /** Mỗi 25 giây: giữ kết nối sống và đẩy access token mới khi token đã được làm mới. */
    private void heartbeat() {
        WebSocket s = socket;
        if (!running || s == null) return;
        send(s, "phoenix", "heartbeat", new JSONObject());
        try {
            String token = SupabaseClient.get(app).freshAccessToken();
            if (!token.equals(lastToken)) {
                lastToken = token;
                send(s, topic, "access_token", Json.obj("access_token", token));
            }
        } catch (RuntimeException ignored) {
            // token sẽ được lấy lại ở lần kết nối kế tiếp
        }
    }

    private void send(WebSocket ws, String t, String event, JSONObject payload) {
        ws.send(Json.obj("topic", t, "event", event, "payload", payload, "ref", String.valueOf(refs.getAndIncrement())).toString());
    }
}
