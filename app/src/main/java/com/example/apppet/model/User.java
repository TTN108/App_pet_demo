package com.example.apppet.model;

/** Hồ sơ người dùng (bảng profiles trên Supabase). id là uuid của auth.users. */
public class User {
    public final String id;
    public final String username;
    public final String email;
    public final String displayName;
    public final String role;
    public String avatarUri;
    public String createdAt;

    public User(String id, String username, String email, String displayName, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public String getShownName() {
        if (displayName != null && !displayName.trim().isEmpty()) return displayName;
        return username;
    }
}
