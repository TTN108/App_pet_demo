package com.example.apppet.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StorageUrlsTest {
    private static final String BASE = "https://abc.supabase.co/";

    @Test
    public void relativePathPointsToPublicGalleryBucket() {
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/dog/poodle/poodle_0001.jpg",
                StorageUrls.gallery(BASE, "/dog/poodle/poodle_0001.jpg"));
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/dog/poodle/poodle_0001.jpg",
                StorageUrls.gallery("https://abc.supabase.co", "dog/poodle/poodle_0001.jpg"));
    }

    @Test
    public void segmentsAreEncoded() {
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/cat/a%20b.jpg", StorageUrls.gallery(BASE, "cat/a b.jpg"));
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/t%C3%AAn/%E1%BA%A3nh.jpg",
                StorageUrls.gallery(BASE, "/tên/ảnh.jpg"));
    }

    @Test
    public void absoluteUrlsAreKept() {
        String url = "https://abc.supabase.co/storage/v1/object/public/contrib/uid/1.jpg";
        assertEquals(url, StorageUrls.gallery(BASE, url));
        assertEquals(url, StorageUrls.thumbOrFull(BASE, url, "dog/x.jpg"));
    }

    @Test
    public void thumbIsPreferredButFullIsTheFallback() {
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/dog/x_t.jpg",
                StorageUrls.thumbOrFull(BASE, "dog/x_t.jpg", "dog/x.jpg"));
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/dog/x.jpg",
                StorageUrls.thumbOrFull(BASE, null, "dog/x.jpg"));
        assertEquals("https://abc.supabase.co/storage/v1/object/public/gallery/dog/x.jpg",
                StorageUrls.thumbOrFull(BASE, "  ", "dog/x.jpg"));
    }

    @Test
    public void emptyInputsGiveEmptyString() {
        assertEquals("", StorageUrls.gallery(BASE, null));
        assertEquals("", StorageUrls.gallery(BASE, "  "));
        assertEquals("", StorageUrls.gallery("", "dog/x.jpg"));
    }
}
