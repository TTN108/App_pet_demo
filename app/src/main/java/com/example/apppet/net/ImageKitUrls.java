package com.example.apppet.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Tạo URL ảnh ImageKit. Trong database chỉ lưu đường dẫn tương đối (vd "/pets/dog/poodle/poodle_0001.jpg");
 * đổi nhà cung cấp ảnh chỉ cần đổi IMAGEKIT_ENDPOINT. Đường dẫn bắt đầu bằng http (ảnh người dùng ở Supabase Storage)
 * được giữ nguyên. Thay đổi kích thước/định dạng bằng tham số tr= của ImageKit (w-: rộng, q-: chất lượng, f-: định dạng).
 */
public final class ImageKitUrls {
    private ImageKitUrls() { }

    public static String thumb(String endpoint, String path, int width) {
        return build(endpoint, path, "tr=w-" + width + ",q-75,f-webp");
    }

    public static String medium(String endpoint, String path) {
        return build(endpoint, path, "tr=w-1080,q-85,f-webp");
    }

    public static String original(String endpoint, String path) {
        return build(endpoint, path, null);
    }

    public static String thumb(String path, int width) {
        return thumb(Config.IMAGEKIT_ENDPOINT, path, width);
    }

    public static String medium(String path) {
        return medium(Config.IMAGEKIT_ENDPOINT, path);
    }

    public static String original(String path) {
        return original(Config.IMAGEKIT_ENDPOINT, path);
    }

    static String build(String endpoint, String path, String query) {
        if (path == null || path.trim().isEmpty()) return "";
        String p = path.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (endpoint == null || endpoint.isEmpty()) return "";
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        StringBuilder out = new StringBuilder(base);
        for (String seg : p.split("/")) {
            if (seg.isEmpty()) continue;
            out.append('/').append(encodeSegment(seg));
        }
        if (query != null) out.append('?').append(query);
        return out.toString();
    }

    private static String encodeSegment(String seg) {
        try {
            return URLEncoder.encode(seg, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
