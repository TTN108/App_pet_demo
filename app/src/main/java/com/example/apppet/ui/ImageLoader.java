package com.example.apppet.ui;

import android.widget.ImageView;

import com.bumptech.glide.Glide;

/** Nạp ảnh từ URL https hoặc file:// bằng Glide (có cache đĩa, không chặn luồng UI). */
public final class ImageLoader {
    private ImageLoader() { }

    public static void load(ImageView view, String uri, int fallbackRes) {
        if (uri == null || uri.trim().isEmpty()) {
            Glide.with(view.getContext()).clear(view);
            view.setImageResource(fallbackRes);
            return;
        }
        Glide.with(view.getContext()).load(uri).centerCrop().placeholder(fallbackRes).error(fallbackRes).into(view);
    }
}
