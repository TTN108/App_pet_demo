package com.example.apppet.model;

import com.example.apppet.data.DatabaseHelper;

/** Trạng thái của một người dùng trong một cộng đồng (bảng community_members). */
public class Membership {
    public final String role;       // MEMBER, MOD, ADMIN
    public final String status;     // ACTIVE, MUTED, BANNED
    public final String mutedUntil; // ISO yyyy-MM-dd'T'HH:mm:ss hoặc null

    public Membership(String role, String status, String mutedUntil) {
        this.role = role;
        this.status = status;
        this.mutedUntil = mutedUntil;
    }

    public boolean isBanned() {
        return "BANNED".equals(status);
    }

    public boolean isModerator() {
        return "MOD".equals(role) || "ADMIN".equals(role);
    }

    /** MUTED có thời hạn sẽ tự hết hiệu lực khi qua mutedUntil. */
    public boolean canSend() {
        if ("ACTIVE".equals(status)) return true;
        if ("MUTED".equals(status) && mutedUntil != null && !mutedUntil.isEmpty()) {
            return mutedUntil.compareTo(DatabaseHelper.now()) < 0;
        }
        return false;
    }
}
