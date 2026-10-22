package com.example.apppet.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.example.apppet.data.SessionManager;

public class UiThemeManager {
    public static void applyTheme(View view, Context context) {
        if (view == null || context == null) return;
        try {
            String bgColor = SessionManager.getBgColor(context);
            if (bgColor != null && !bgColor.trim().isEmpty()) {
                view.setBackgroundColor(Color.parseColor(bgColor));
            }
        } catch (Exception ignored) {
        }
    }
}
