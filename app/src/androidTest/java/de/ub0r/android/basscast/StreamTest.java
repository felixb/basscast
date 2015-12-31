package de.ub0r.android.basscast;

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
}
