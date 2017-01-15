package de.ub0r.android.basscast.model;

import com.google.android.gms.cast.MediaMetadata;

import android.test.AndroidTestCase;

/**
 * @author flx
 */
public class MimeTypeTest extends AndroidTestCase {

    @SuppressWarnings("ConstantConditions")
    public void testGuessMimeType() {
        assertNull(MimeType.guessMimeType("http://example.org/foo.bar"));
        assertEquals("text/html",
                MimeType.guessMimeType("http://example.org/index.html").getMimeType());
        assertEquals("audio/mp3",
                MimeType.guessMimeType("http://example.org/audio.mp3").getMimeType());
        assertEquals("video/mp4",
                MimeType.guessMimeType("http://example.org/sream.mp4").getMimeType());
        assertEquals("image/png",
                MimeType.guessMimeType("http://example.org/pic.PNG").getMimeType());
    }

    public void testGetMimeType() {
        assertEquals("text/html", new MimeType("TEXT/html ; charset=blubb").getMimeType());
    }

    public void testIsPlayable() {
        assertFalse(new MimeType("text/html").isPlayable());
        assertTrue(new MimeType("audio/foo").isPlayable());
        assertTrue(new MimeType("video/bar").isPlayable());
        assertFalse(new MimeType("audio/x-scpls").isPlayable());
    }

    public void testIsBrowsable() {
        assertTrue(new MimeType("text/html").isBrowsable());
        assertFalse(new MimeType("audio/foo").isBrowsable());
        assertFalse(new MimeType("video/bar").isBrowsable());
        assertFalse(new MimeType("foo/bar").isBrowsable());
        assertTrue(new MimeType("audio/x-scpls").isBrowsable());
    }

    public void testIsSupported() {
        assertTrue(new MimeType("text/html").isSupported());
        assertTrue(new MimeType("audio/foo").isSupported());
        assertTrue(new MimeType("video/bar").isSupported());
        assertFalse(new MimeType("foo/bar").isSupported());
        assertTrue(new MimeType("audio/x-scpls").isSupported());
    }

    public void testGetType() {
        assertEquals(MediaMetadata.MEDIA_TYPE_USER, new MimeType("text/html").getType());
        assertEquals(MediaMetadata.MEDIA_TYPE_MOVIE, new MimeType("video/foo").getType());
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK, new MimeType("audio/bar").getType());
        assertEquals(MediaMetadata.MEDIA_TYPE_USER, new MimeType("audio/x-scpls").getType());
        assertEquals(-1, new MimeType("foo/bar").getType());
    }
}
