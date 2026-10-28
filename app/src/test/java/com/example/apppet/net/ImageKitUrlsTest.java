package com.example.apppet.net;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageKitUrlsTest {
    private static final String EP = "https://ik.imagekit.io/demo/";

    @Test
    public void thumbAddsTransformationAndNormalizesSlashes() {
        assertEquals("https://ik.imagekit.io/demo/pets/dog/poodle/poodle_0001.jpg?tr=w-360,q-75,f-webp",
                ImageKitUrls.thumb(EP, "/pets/dog/poodle/poodle_0001.jpg", 360));
    }

    @Test
    public void spacesAndNonAsciiAreEncodedPerSegment() {
        assertEquals("https://ik.imagekit.io/demo/pets/cat/a%20b.jpg?tr=w-240,q-75,f-webp",
                ImageKitUrls.thumb(EP, "pets/cat/a b.jpg", 240));
        assertEquals("https://ik.imagekit.io/demo/t%C3%AAn/%E1%BA%A3nh.jpg?tr=w-50,q-75,f-webp",
                ImageKitUrls.thumb(EP, "/tên/ảnh.jpg", 50));
    }

    @Test
    public void absoluteUrlsAreKeptAsIs() {
        String url = "https://abc.supabase.co/storage/v1/object/public/avatars/u/1.jpg";
        assertEquals(url, ImageKitUrls.thumb(EP, url, 100));
        assertEquals(url, ImageKitUrls.original(EP, url));
    }

    @Test
    public void originalHasNoQueryAndMediumResizes() {
        assertEquals("https://ik.imagekit.io/demo/pets/x.jpg", ImageKitUrls.original(EP, "/pets/x.jpg"));
        assertEquals("https://ik.imagekit.io/demo/p/q.jpg?tr=w-1080,q-85,f-webp", ImageKitUrls.medium(EP, "/p/q.jpg"));
    }

    @Test
    public void emptyInputsGiveEmptyString() {
        assertEquals("", ImageKitUrls.thumb(EP, null, 100));
        assertEquals("", ImageKitUrls.thumb(EP, "  ", 100));
        assertEquals("", ImageKitUrls.thumb("", "/a.jpg", 100));
    }
}
