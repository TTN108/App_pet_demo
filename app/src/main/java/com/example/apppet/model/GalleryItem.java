package com.example.apppet.model;

public class GalleryItem {
    private int id;
    private int categoryId;
    private String imageUri;
    private String colorHex;
    private boolean isDownload;

    public GalleryItem() {
    }

    public GalleryItem(int id, int categoryId, String imageUri, String colorHex, boolean isDownload) {
        this.id = id;
        this.categoryId = categoryId;
        this.imageUri = imageUri;
        this.colorHex = colorHex;
        this.isDownload = isDownload;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isDownload() {
        return isDownload;
    }

    public void setDownload(boolean download) {
        isDownload = download;
    }
}
