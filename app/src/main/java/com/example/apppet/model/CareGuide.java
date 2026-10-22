package com.example.apppet.model;

public class CareGuide {
    private int id;
    private int categoryId;
    private String title;
    private String subtitle;
    private String content;

    public CareGuide() {
    }

    public CareGuide(int id, int categoryId, String title, String subtitle, String content) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.subtitle = subtitle;
        this.content = content;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
