package com.example.apppet.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Đọc, thu nhỏ, xoay đúng chiều và lưu ảnh vào bộ nhớ trong của ứng dụng. */
public final class ImageStorage {
    public static final int MAX_SIDE = 1280;

    private ImageStorage() { }

    /** Đọc ảnh từ Uri (content:// hoặc file://), thu nhỏ về tối đa maxSide và xoay theo EXIF. */
    public static Bitmap decode(Context context, Uri uri, int maxSide) throws IOException {
        ContentResolver cr = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được ảnh");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while ((bounds.outWidth / sample) > maxSide * 2 || (bounds.outHeight / sample) > maxSide * 2) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được ảnh");
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Ảnh không hợp lệ");

        int rotation = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (IOException ignored) {
            // không có EXIF thì thôi
        }
        float scale = Math.min(1f, (float) maxSide / Math.max(bmp.getWidth(), bmp.getHeight()));
        if (rotation != 0 || scale < 1f) {
            Matrix m = new Matrix();
            if (scale < 1f) m.postScale(scale, scale);
            if (rotation != 0) m.postRotate(rotation);
            Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (out != bmp) bmp.recycle();
            bmp = out;
        }
        return bmp;
    }

    /** Lưu bitmap thành JPEG trong filesDir/<dir>/ và trả về chuỗi file:// URI. */
    public static String saveJpeg(Context context, Bitmap bitmap, String dir) throws IOException {
        File folder = new File(context.getFilesDir(), dir);
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Không tạo được thư mục " + dir);
        File file = new File(folder, UUID.randomUUID().toString() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return Uri.fromFile(file).toString();
    }

    /** Sao chép ảnh người dùng chọn vào bộ nhớ trong (đã thu nhỏ + xoay đúng chiều). */
    public static String copyToAppStorage(Context context, Uri source, String dir) throws IOException {
        Bitmap bmp = decode(context, source, MAX_SIDE);
        try {
            return saveJpeg(context, bmp, dir);
        } finally {
            bmp.recycle();
        }
    }

    /** Sao chép một file đã nằm trong bộ nhớ của app sang thư mục khác (dùng để xếp hàng gửi ảnh đóng góp). */
    public static String copyOwned(Context context, String fileUri, String dir) throws IOException {
        File src = new File(Uri.parse(fileUri).getPath());
        File folder = new File(context.getFilesDir(), dir);
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Không tạo được thư mục " + dir);
        File dest = new File(folder, UUID.randomUUID().toString() + ".jpg");
        try (java.io.InputStream in = new java.io.FileInputStream(src); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return Uri.fromFile(dest).toString();
    }

    /** Xóa file ảnh nếu nó nằm trong filesDir của app (không đụng tới ảnh người dùng ở nơi khác). */
    public static void deleteIfOwned(Context context, String uriString) {
        if (uriString == null || !uriString.startsWith("file:")) return;
        try {
            File file = new File(Uri.parse(uriString).getPath());
            if (file.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (IOException | RuntimeException ignored) {
            // bỏ qua: file rác không ảnh hưởng chức năng
        }
    }
}
