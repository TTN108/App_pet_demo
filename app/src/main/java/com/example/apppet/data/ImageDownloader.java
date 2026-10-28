package com.example.apppet.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Tải ảnh từ bộ sưu tập về máy. Android 10+: lưu vào Pictures/AppPet qua MediaStore (không cần quyền).
 * Android 8-9: lưu vào thư mục riêng của app (không cần quyền WRITE_EXTERNAL_STORAGE).
 * Chỉ nhận https và ảnh <= 15 MB. PHẢI gọi ngoài luồng UI.
 */
public final class ImageDownloader {
    private static final String USER_AGENT = "AppPetDemo/1.0 (student project; Android)";
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private ImageDownloader() { }

    /** @return chuỗi mô tả nơi lưu, hoặc ném IOException với thông báo tiếng Việt. */
    public static String download(Context context, String imageUrl, String baseName) throws IOException {
        if (imageUrl == null || !imageUrl.startsWith("https://")) {
            throw new IOException("Chỉ hỗ trợ tải ảnh qua https.");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("Máy chủ trả về lỗi " + code);
            String type = conn.getContentType() == null ? "" : conn.getContentType().toLowerCase(Locale.ROOT);
            if (!type.startsWith("image/")) throw new IOException("Liên kết không phải ảnh.");
            String ext = type.contains("png") ? "png" : type.contains("webp") ? "webp" : "jpg";
            String mime = "image/" + (ext.equals("jpg") ? "jpeg" : ext);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                    if (buffer.size() > MAX_BYTES) throw new IOException("Ảnh quá lớn.");
                }
            }
            String safe = baseName == null ? "pet" : baseName.replaceAll("[^A-Za-z0-9_-]+", "_");
            String fileName = "AppPet_" + safe + "_" + System.currentTimeMillis() + "." + ext;
            return save(context, buffer.toByteArray(), fileName, mime);
        } finally {
            conn.disconnect();
        }
    }

    private static String save(Context context, byte[] data, String fileName, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppPet");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Không tạo được tệp trong thư viện ảnh.");
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Không ghi được tệp.");
                out.write(data);
            } catch (IOException ex) {
                resolver.delete(uri, null, null);
                throw ex;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return "Pictures/AppPet/" + fileName;
        }
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AppPet");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục lưu ảnh.");
        File file = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return file.getAbsolutePath();
    }
}
