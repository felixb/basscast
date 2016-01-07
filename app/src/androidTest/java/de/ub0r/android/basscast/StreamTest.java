package de.ub0r.android.basscast;

import com.google.android.gms.cast.MediaMetadata;

import android.test.AndroidTestCase;

import de.ub0r.android.basscast.model.Stream;

/**
 * @author flx
 */
public class StreamTest extends AndroidTestCase {

    public void testStreamToBundle() {
        Stream s = new Stream();
        s.id = 5;
        s.title = "some stream";
        s.url = "http://example.com/stream";
        s.type = 1;
        s.mimeType = "some/mime";

        assertEquals(s, new Stream(s.toBundle()));
    }

    public void testParseMimeType() {
        Stream s = new Stream();
        s.id = 5;
        s.title = "some stream";
        s.url = "http://example.com/stream";

        s.mimeType = "audio/*";
        s.parseMimeType();
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK, s.type);

        s.mimeType = "video/*";
        s.parseMimeType();
        assertEquals(MediaMetadata.MEDIA_TYPE_MOVIE, s.type);

        try {
            s.mimeType = "unsupported/type";
            s.parseMimeType();
            fail("should have raised IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

    }
}
