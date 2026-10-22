package com.example.apppet.data;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Băm mật khẩu bằng PBKDF2 + salt ngẫu nhiên. Không bao giờ lưu mật khẩu thô.
 * Định dạng lưu: pbkdf2$<số vòng lặp>$<salt base64>$<hash base64>
 * (PBKDF2WithHmacSHA1 vì minSdk 23 chưa có SHA256 cho PBKDF2.)
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 60000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 160;

    private PasswordHasher() { }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.encodeToString(salt, Base64.NO_WRAP) + "$"
                + Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith(PREFIX + "$")) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể băm mật khẩu", ex);
        }
    }
}
