package com.example.apppet.ui;

public final class PetIcons {
    private PetIcons() { }

    public static String forType(String type) {
        if (type == null) return "🐾";
        switch (type) {
            case "Chó": return "🐶";
            case "Mèo": return "🐱";
            case "Chim": return "🐦";
            case "Cá": return "🐟";
            case "Thỏ": return "🐰";
            default: return "🐾";
        }
    }
}
