package de.ub0r.android.basscast.fetcher;

import android.net.Uri;
import android.test.AndroidTestCase;

import com.google.android.gms.cast.MediaMetadata;

import java.io.IOException;
import java.util.List;

import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * @author flx
 */
public class StreamFetcherTest extends AndroidTestCase {

    public void testFetch() throws IOException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setBody("<html><body>here is a list: " +
                        "link to some other page: <a href=\"http://example.org\">example.org</a>" +
                        "<ul>" +
                        "<li><a href=\"/listOfStreams/example-stream.mp4\">stream me up!</a></li>" +
                        "<li><a href=\"some-other-stream.mp3\">music baby</a></li>" +
                        "</ul>" +
                        "</body></html>")
                .setHeader("Content-Type", "text/html"));

        server.start();

        final HttpUrl baseUrl = server.url("/listOfStreams/");
        final Stream baseStream = new Stream(baseUrl.toString(), "some stream", "text/html");

        final StreamFetcher fetcher = new StreamFetcher(getContext());
        final List<Stream> streams = fetcher.fetch(baseStream);

        assertNotNull(streams);
        assertEquals(2, streams.size());

        Stream stream = streams.get(0);
        assertEquals("stream me up!", stream.title);
        assertEquals(baseUrl.toString() + "example-stream.mp4", stream.url);
        assertEquals("video/mp4", stream.mimeType);

        stream = streams.get(1);
        assertEquals("music baby", stream.title);
        assertEquals(baseUrl.toString() + "some-other-stream.mp3", stream.url);
        assertEquals("audio/mp3", stream.mimeType);

        server.shutdown();
    }

    public void testFetchMimeTypeByExtension() throws IOException {
        final StreamFetcher fetcher = new StreamFetcher(getContext());
        assertEquals("text/html", fetcher.fetchMimeType("http://example.org/index.html"));
        assertEquals("audio/mp3", fetcher.fetchMimeType("http://example.org/audio.mp3"));
        assertEquals("video/mp4", fetcher.fetchMimeType("http://example.org/sream.mp4"));
    }

    public void testFetchMimeTypeHtml() throws IOException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setBody("<html><body></body></html>")
                .setHeader("Content-Type", "text/html ; charset: utf8"));

        server.start();

        final StreamFetcher fetcher = new StreamFetcher(getContext());
        assertEquals("text/html", fetcher.fetchMimeType(server.url("/").toString()));
    }
}
