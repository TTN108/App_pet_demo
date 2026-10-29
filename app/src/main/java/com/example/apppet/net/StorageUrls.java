package com.example.apppet.net;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * URL ảnh trên Supabase Storage. Trong database chỉ lưu đường dẫn TƯƠNG ĐỐI trong bucket public "gallery"
 * (vd "dog/poodle/poodle_0001.jpg", ảnh nhỏ "dog/poodle/poodle_0001_t.jpg") hoặc URL tuyệt đối (ảnh người dùng đóng góp
 * nằm ở bucket "contrib"). Gói free của Supabase không tự thu nhỏ ảnh nên ảnh nhỏ được TẠO SẴN lúc tải lên.
 */
public final class StorageUrls {
    private static final String GALLERY_PATH = "/storage/v1/object/public/gallery/";

    private StorageUrls() { }

    /** Ảnh đầy đủ (hoặc URL tuyệt đối giữ nguyên). */
    public static String gallery(String baseUrl, String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String p = path.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (baseUrl == null || baseUrl.isEmpty()) return "";
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        StringBuilder out = new StringBuilder(base).append(GALLERY_PATH, 0, GALLERY_PATH.length() - 1);
        for (String seg : p.split("/")) {
            if (seg.isEmpty()) continue;
            out.append('/').append(encodeSegment(seg));
        }
        return out.toString();
    }

    /** Ảnh nhỏ nếu có, không thì dùng ảnh đầy đủ. */
    public static String thumbOrFull(String baseUrl, String thumbPath, String imagePath) {
        boolean hasThumb = thumbPath != null && !thumbPath.trim().isEmpty();
        return gallery(baseUrl, hasThumb ? thumbPath : imagePath);
    }

    public static String gallery(String path) {
        return gallery(Config.SUPABASE_URL, path);
    }

    public static String thumbOrFull(String thumbPath, String imagePath) {
        return thumbOrFull(Config.SUPABASE_URL, thumbPath, imagePath);
    }

    private static String encodeSegment(String seg) {
        try {
            return URLEncoder.encode(seg, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
