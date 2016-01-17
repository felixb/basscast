package de.ub0r.android.basscast.model;

import android.net.Uri;
import android.test.AndroidTestCase;

/**
 * @author flx
 */
public class StreamTest extends AndroidTestCase {

    public void testNewStreamWithBaseStream() {
        final Stream base = new Stream("http://example.org", "base", new MimeType("text/html"));
        base.setId(5);
        // base.baseId = -1
        // base.parentId = -1

        Stream parent0 = new Stream(base, "http://example.org/foo", "parent 0",
                new MimeType("text/html"));
        parent0.setId(6);
        assertEquals(5, parent0.getBaseId());
        assertEquals(5, parent0.getParentId());

        Stream parent1 = new Stream(parent0, "http://example.org/foo/bar", "parent 1",
                new MimeType("text/html"));
        parent1.setId(7);
        assertEquals(5, parent1.getBaseId());
        assertEquals(6, parent1.getParentId());

        Stream child = new Stream(parent1, "http://example.org/foo/bar/stream", "child",
                new MimeType("audio/mp3"));
        assertEquals(5, child.getBaseId());
        assertEquals(7, child.getParentId());
    }

    public void testStreamToBundle() {
        final Stream s = new Stream();
        s.setId(5);
        s.setTitle("some stream");
        s.setUrl("http://example.com/stream");
        s.setMimeType("some/mime");

        assertEquals(s, new Stream(s.toBundle()));
    }

    public void testStreamToUri() {
        final Stream s = new Stream();
        s.setId(5);
        s.setTitle("some stream");
        s.setUrl("http://example.com/stream");
        s.setMimeType("audio/mp3");

        final Uri u = s.toSharableUri();
        assertEquals("http", u.getScheme());
        assertEquals("example.com", u.getHost());
        assertEquals("/stream", u.getPath());
        assertEquals("audio/mp3", u.getQueryParameter(StreamsTable.FIELD_MIME_TYPE));
        assertEquals("some stream", u.getQueryParameter(StreamsTable.FIELD_TITLE));

        final Stream newStream = new Stream(u);
        assertEquals(s.getTitle(), newStream.getTitle());
        assertEquals(s.getUrl(), newStream.getUrl());
        assertEquals(s.getMimeType(), newStream.getMimeType());

        assertEquals(u, newStream.toSharableUri());
    }
}
