package com.example.apppet.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.example.apppet.data.SessionManager;

/** Áp màu nền người dùng đã chọn (lưu trong SessionManager) lên màn hình. */
public final class UiThemeManager {
    private UiThemeManager() { }

    public static void applyTheme(View root, Context context) {
        if (root == null || context == null) return;
        try {
            root.setBackgroundColor(Color.parseColor(SessionManager.getBgColor(context)));
        } catch (IllegalArgumentException ex) {
            root.setBackgroundColor(Color.parseColor(SessionManager.DEFAULT_BG_COLOR));
        }
    }
}
