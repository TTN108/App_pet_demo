package com.example.apppet.model;

/** Trạng thái của người dùng trong một cộng đồng (hàm my_membership trên Supabase). */
public class Membership {
    public final String role;       // MEMBER, MOD, ADMIN
    public final String status;     // ACTIVE, MUTED, BANNED
    public final String mutedUntil; // ISO hoặc null
    private final boolean canSend;  // do SERVER tính (đã tính hết hạn mute), app không tự suy luận

    public Membership(String role, String status, String mutedUntil, boolean canSend) {
        this.role = role;
        this.status = status;
        this.mutedUntil = mutedUntil;
        this.canSend = canSend;
    }

    public boolean isBanned() {
        return "BANNED".equals(status);
    }

    public boolean isModerator() {
        return "MOD".equals(role) || "ADMIN".equals(role);
    }

    public boolean canSend() {
        return canSend;
    }
}
