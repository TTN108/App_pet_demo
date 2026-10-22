package com.example.apppet.model;

public class User {
    public final long id;
    public final String username;
    public final String email;
    public final String displayName;
    public final String role;
    /** true với tài khoản demo cũ (chưa có mật khẩu thật) - không được dùng để đăng nhập. */
    public final boolean placeholderAccount;
    public String avatarUri;
    public String createdAt;

    public User(long id, String username, String email, String displayName, String role, boolean placeholderAccount) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.placeholderAccount = placeholderAccount;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public String getShownName() {
        if (displayName != null && !displayName.trim().isEmpty()) return displayName;
        return username;
    }

    public int getId() { return (int) id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUri() { return avatarUri; }
    public String getCreatedAt() { return createdAt; }
}