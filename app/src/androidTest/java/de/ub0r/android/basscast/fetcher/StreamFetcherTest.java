package de.ub0r.android.basscast.fetcher;

import android.database.Cursor;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * @author flx
 */
public class StreamFetcherTest extends AndroidTestCase {

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getContext().getContentResolver()
                .delete(StreamsTable.CONTENT_URI, StreamsTable.FIELD_URL + " like '%example.org%'",
                        null);
    }


    public void testFetch() throws IOException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setBody("<html><body>here is a list: " +
                        "link to some other page: <a href=\"http://example.org\">example.org</a>" +
                        "link a pic: <a href=\"pic.png\">lolcats</a>" +
                        "<ul>" +
                        "<li><a href=\"/listOfStreams/example-stream.mp4\">stream me up!</a></li>" +
                        "<li><a href=\"some-other-stream.mp3\">music baby</a></li>" +
                        "<li><a href=\"http://cdn.example.org/some-external-stream.mp3\">even more music</a></li>"
                        +
                        "<li><a href=\"#some-other-content.mp3\">there is no real url dude</a></li>"
                        +
                        "</ul>" +
                        "</body></html>")
                .setHeader("Content-Type", "text/html;charset=utf8"));

        server.start();

        final HttpUrl baseUrl = server.url("/listOfStreams/");
        final Stream parentStream = new Stream(baseUrl.toString(), "some stream",
                new MimeType("text/html"));
        parentStream.setId(9002);
        parentStream.setBaseId(9000);
        parentStream.setParentId(9001);

        final StreamFetcher fetcher = new StreamFetcher(getContext());
        final List<Stream> streams = fetcher.fetch(parentStream);

        assertNotNull(streams);
        assertEquals(3, streams.size());

        Stream stream = streams.get(0);
        assertEquals(9000, stream.getBaseId());
        assertEquals(9002, stream.getParentId());
        assertEquals("stream me up!", stream.getTitle());
        assertEquals(baseUrl.toString() + "example-stream.mp4", stream.getUrl());
        assertEquals("video/mp4", stream.getMimeType());

        stream = streams.get(1);
        assertEquals("music baby", stream.getTitle());
        assertEquals(baseUrl.toString() + "some-other-stream.mp3", stream.getUrl());
        assertEquals("audio/mp3", stream.getMimeType());

        stream = streams.get(2);
        assertEquals("even more music", stream.getTitle());
        assertEquals("http://cdn.example.org/some-external-stream.mp3", stream.getUrl());
        assertEquals("audio/mp3", stream.getMimeType());

        server.shutdown();
    }

    public void testFetchMimeTypeByExtension() throws IOException {
        final StreamFetcher fetcher = new StreamFetcher(getContext());
        assertEquals("text/html",
                fetcher.fetchMimeType("http://example.org/index.html").getMimeType());
        assertEquals("audio/mp3",
                fetcher.fetchMimeType("http://example.org/audio.mp3").getMimeType());
        assertEquals("video/mp4",
                fetcher.fetchMimeType("http://example.org/sream.mp4").getMimeType());
        assertEquals("image/png",
                fetcher.fetchMimeType("http://example.org/pic.PNG").getMimeType());
    }

    public void testFetchMimeTypeHtml() throws IOException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setBody("<html><body></body></html>")
                .setHeader("Content-Type", "text/HTML ; charset: utf8"));

        server.start();

        final StreamFetcher fetcher = new StreamFetcher(getContext());
        assertEquals("text/html", fetcher.fetchMimeType(server.url("/").toString()).getMimeType());
    }

    public void testInsert() {
        final Stream parent = new Stream("http://example.org", "example",
                new MimeType("text/html"));
        parent.setId(9000);

        List<Stream> list = new ArrayList<>();
        Stream stream0 = new Stream(parent, "http://example.org/stream", "example stream",
                new MimeType("audio/mp3"));
        Stream stream1 = new Stream(parent, "http://example.org/other-stream",
                "other example stream", new MimeType("audio/mp3"));
        Stream stream2 = new Stream(parent, "http://example.org/yet-another-stream",
                "some other example stream", new MimeType("audio/mp3"));
        list.add(stream0);
        list.add(stream1);

        final StreamFetcher fetcher = new StreamFetcher(getContext());
        fetcher.insert(parent, list);

        Cursor cursor = getContext().getContentResolver().query(
                StreamsTable.CONTENT_URI, null,
                StreamsTable.FIELD_URL + " like '%example.org%'", null, null);
        assertNotNull(cursor);
        List<Stream> streams = StreamsTable.getRows(cursor, true);
        assertEquals(2, streams.size());
        assertEquals(stream0.getUrl(), streams.get(0).getUrl());
        assertEquals(stream1.getUrl(), streams.get(1).getUrl());

        // insert updated list
        stream0.setUpdated(System.currentTimeMillis());
        stream0.setTitle("new stream title");
        list = new ArrayList<>();
        list.add(stream0);
        list.add(stream2);

        fetcher.insert(parent, list);

        cursor = getContext().getContentResolver().query(
                StreamsTable.CONTENT_URI, null,
                StreamsTable.FIELD_URL + " like '%example.org%'", null, null);
        assertNotNull(cursor);
        streams = StreamsTable.getRows(cursor, true);
        assertEquals(2, streams.size());
        assertEquals(stream0.getUrl(), streams.get(0).getUrl());
        assertEquals(stream0.getTitle(), streams.get(0).getTitle());
        assertEquals(stream2.getUrl(), streams.get(1).getUrl());
    }
}
