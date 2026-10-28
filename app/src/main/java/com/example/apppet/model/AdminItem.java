package com.example.apppet.model;

/** Một mục trong hàng chờ duyệt của quản trị viên (ảnh, đề xuất wiki hoặc báo cáo chat). */
public class AdminItem {
    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_WIKI = 1;
    public static final int TYPE_REPORT = 2;

    public final int type;
    public final long id;
    public String title;
    public String body;
    public String imageUrl;     // chỉ có với ảnh

    public AdminItem(int type, long id) {
        this.type = type;
        this.id = id;
    }
}
