package de.ub0r.android.basscast;

import com.google.android.gms.cast.MediaMetadata;

import android.net.Uri;
import android.test.AndroidTestCase;

import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

/**
 * @author flx
 */
public class StreamTest extends AndroidTestCase {

    public void testStreamToBundle() {
        final Stream s = new Stream();
        s.id = 5;
        s.title = "some stream";
        s.url = "http://example.com/stream";
        s.type = MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
        s.mimeType = "some/mime";

        assertEquals(s, new Stream(s.toBundle()));
    }

    public void testStreamToUri() {
        final Stream s = new Stream();
        s.id = 5;
        s.title = "some stream";
        s.url = "http://example.com/stream";
        s.type = MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
        s.mimeType = "audio/mp3";

        final Uri u = s.toSharableUri();
        assertEquals("http", u.getScheme());
        assertEquals("example.com", u.getHost());
        assertEquals("/stream", u.getPath());
        assertEquals("audio/mp3", u.getQueryParameter(StreamsTable.FIELD_MIME_TYPE));
        assertEquals("some stream", u.getQueryParameter(StreamsTable.FIELD_TITLE));

        final Stream newStream = new Stream(u);
        assertEquals(s.title, newStream.title);
        assertEquals(s.url, newStream.url);
        assertEquals(s.mimeType, newStream.mimeType);
        assertEquals(s.type, newStream.type);

        assertEquals(u, newStream.toSharableUri());
    }

    public void testParseMimeType() {
        final Stream s = new Stream();
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
