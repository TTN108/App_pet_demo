package com.example.apppet.net;

/** Phiên đăng nhập Supabase. expiresAtSec: thời điểm access token hết hạn (giây epoch). */
public final class AuthSession {
    public final String userId;
    public final String accessToken;
    public final String refreshToken;
    public final long expiresAtSec;

    public AuthSession(String userId, String accessToken, String refreshToken, long expiresAtSec) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtSec = expiresAtSec;
    }

    public boolean expiresWithin(long seconds) {
        return expiresAtSec - System.currentTimeMillis() / 1000L <= seconds;
    }
}
