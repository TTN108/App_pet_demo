package com.example.apppet.model;

public class ForumPost {
    private int id;
    private int categoryId;
    private String groupName;
    private String timeAgo;
    private String content;

    public ForumPost() {
    }

    public ForumPost(int id, int categoryId, String groupName, String timeAgo, String content) {
        this.id = id;
        this.categoryId = categoryId;
        this.groupName = groupName;
        this.timeAgo = timeAgo;
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTimeAgo() {
        return timeAgo;
    }

    public void setTimeAgo(String timeAgo) {
        this.timeAgo = timeAgo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
