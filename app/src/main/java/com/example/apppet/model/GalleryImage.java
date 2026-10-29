package com.example.apppet.model;

import com.example.apppet.net.StorageUrls;

/** Một ảnh trong bộ sưu tập (gallery_images, chỉ lấy status = APPROVED). Ảnh nằm trong Supabase Storage. */
public class GalleryImage {
    public long id;
    public Long petTypeId;
    public Long breedId;
    public String sourceType;   // API | USER | ADMIN
    public String imagePath;    // đường dẫn trong bucket gallery (hoặc URL tuyệt đối với ảnh người dùng)
    public String thumbPath;    // ảnh nhỏ tạo sẵn (có thể null với dữ liệu cũ)
    public int width;
    public int height;
    public String title;
    public String description;  // ghi công tác giả + giấy phép
    public String sourceUrl;
    public String petTypeName;
    public String breedName;

    /** Ảnh nhỏ cho lưới/trang chủ (khoảng 15-30 KB), tiết kiệm băng thông. */
    public String thumbUrl() {
        return StorageUrls.thumbOrFull(thumbPath, imagePath);
    }

    /** Ảnh xem lớn. */
    public String viewUrl() {
        return StorageUrls.gallery(imagePath);
    }

    /** Ảnh để tải về máy. */
    public String downloadUrl() {
        return StorageUrls.gallery(imagePath);
    }
}
