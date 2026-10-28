package com.example.apppet.model;

/** Một bài hướng dẫn chăm sóc (bảng wiki_articles, chỉ hiển thị status = PUBLISHED). */
public class WikiArticle {
    public long id;
    public Long petTypeId;
    public Long breedId;
    public Long categoryId;
    public String categoryName;
    public String petTypeName;
    public String breedName;
    public String title;
    public String summary;
    public String content;
    public String imageUrl;
    public String sourceType;   // ORIGINAL | CRAWLED | API
    public String sourceName;
    public String sourceUrl;
    public String crawledAt;
    /** true: bài do cộng đồng đề xuất từ chat và đã được duyệt (nằm trên Supabase, không có trong catalog của app). */
    public boolean community;
}
