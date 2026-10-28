package com.example.apppet.net;

import org.json.JSONException;
import org.json.JSONObject;

/** Tiện ích org.json không ném checked exception. */
public final class Json {
    private Json() { }

    /** Json.obj("a", 1, "b", "x") -> {"a":1,"b":"x"}. Giá trị null được ghi là JSON null. */
    public static JSONObject obj(Object... keyValues) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                Object v = keyValues[i + 1];
                o.put((String) keyValues[i], v == null ? JSONObject.NULL : v);
            }
        } catch (JSONException ex) {
            throw new IllegalStateException(ex);
        }
        return o;
    }

    /** Chuỗi hoặc null (khác optString: không trả "null" khi giá trị là JSON null). */
    public static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String s = o.optString(key, null);
        return s == null || s.isEmpty() ? null : s;
    }

    public static Long lng(JSONObject o, String key) {
        return o == null || o.isNull(key) ? null : o.optLong(key);
    }
}
