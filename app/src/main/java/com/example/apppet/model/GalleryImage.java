package com.example.apppet.model;

import com.example.apppet.net.ImageKitUrls;

/** Một ảnh trong bộ sưu tập (gallery_images, chỉ lấy status = APPROVED). Ảnh nằm trên ImageKit. */
public class GalleryImage {
    public long id;
    public Long petTypeId;
    public Long breedId;
    public String sourceType;   // API | USER | ADMIN
    public String imagePath;    // đường dẫn ImageKit (hoặc URL tuyệt đối với ảnh người dùng)
    public int width;
    public int height;
    public String title;
    public String description;  // ghi công tác giả + giấy phép
    public String sourceUrl;
    public String petTypeName;
    public String breedName;

    /** Ảnh nhỏ cho lưới/trang chủ. ImageKit tự co ảnh theo tham số trên URL. */
    public String thumbUrl(int widthPx) {
        return ImageKitUrls.thumb(imagePath, widthPx);
    }

    /** Ảnh xem lớn. */
    public String viewUrl() {
        return ImageKitUrls.medium(imagePath);
    }

    /** Ảnh gốc để tải về máy. */
    public String downloadUrl() {
        return ImageKitUrls.original(imagePath);
    }
}
