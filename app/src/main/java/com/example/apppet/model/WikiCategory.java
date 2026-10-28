package com.example.apppet.model;

/** Danh mục wiki (Tổng quan, Dinh dưỡng, Sức khỏe...). id khớp với server (community_wiki.category_id). */
public class WikiCategory {
    public final long id;
    public final String name;

    public WikiCategory(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
