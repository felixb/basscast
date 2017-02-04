package de.ub0r.android.basscast.fetcher;

import android.content.ContentResolver;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import de.ub0r.android.basscast.StreamUtils;
import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StreamFetcher {

    private static final String TAG = "StreamFetcher";

    private final Context mContext;

    private final OkHttpClient mHttpClient;


    public StreamFetcher(@NonNull final Context context) {
        mContext = context;
        mHttpClient = new OkHttpClient().newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public void insert(@NonNull final Stream parent, @NonNull final List<Stream> streams) {
        final ContentResolver cr = mContext.getContentResolver();

        // find streams with same parent missing in new stream list
        List<Stream> existingStreams = queryStreamsByParent(cr, parent);
        List<Stream> deletedStreams = new ArrayList<>();
        for (Stream oldStream : existingStreams) {
            boolean found = false;
            for (Stream newStream : streams) {
                if (oldStream.getUrl().equals(newStream.getUrl())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                deletedStreams.add(oldStream);
            }
        }

        // remove these streams
        existingStreams.removeAll(deletedStreams);
        for (Stream oldStream : deletedStreams) {
            StreamUtils.deleteStream(mContext, oldStream);
        }

        // insert/update new streams
        for (Stream newStream : streams) {
            boolean found = false;
            for (Stream oldStream : existingStreams) {
                if (oldStream.getUrl().equals(newStream.getUrl())) {
                    found = true;
                    cr.update(oldStream.getUri(), StreamsTable.getContentValues(newStream, false),
                            null, null);
                    break;
                }
            }

            if (!found) {
                cr.insert(StreamsTable.CONTENT_URI,
                        StreamsTable.getContentValues(newStream, false));
            }
        }
    }

    public MimeType fetchMimeType(final String url) throws IOException {
        MimeType mimeType = MimeType.guessMimeType(url);
        if (mimeType != null) {
            return mimeType;
        }

        final Request request = new Request.Builder()
                .head()
                .url(url)
                .build();
        final Response response = mHttpClient.newCall(request).execute();
        String contentType = response.header("Content-Type");
        if (contentType == null) {
            return null;
        }

        return new MimeType(contentType);
    }

    public List<Stream> fetch(@NonNull final Stream parent) throws IOException {
        Log.d(TAG, "fetch(" + parent.getUrl() + ")");

        final ArrayList<Stream> list = new ArrayList<>();

        final Response response = mHttpClient.newCall(new Request.Builder()
                .url(parent.getUrl())
                .build()).execute();
        String mimeType = parent.getMimeType();
        if (mimeType != null) {
            if (mimeType.startsWith("text/html")) {
                parseHtml(parent, list, response);
            } else if ("audio/x-scpls".equals(mimeType)) {
                parsePls(parent, list, response);
            }
        }
        Log.d(TAG, "fetch(): returning " + list.size() + " streams");
        return list;
    }

    private void parseHtml(@NonNull final Stream parent, final ArrayList<Stream> list, final Response response) throws IOException {
        final Document doc = Jsoup.parse(response.body().string(), parent.getUrl());
        Elements elements = doc.select("a[href]");
        Log.d(TAG, "fetch(): Found " + elements.size() + " links");
        for (Element element : elements) {
            String url = getUrlFromElement(element);
            MimeType elementsMimeType = MimeType.guessMimeType(url);
            if (url.length() > parent.getUrl().length() && url.startsWith(parent.getUrl())) {
                addStream(parent, list, element, url);
            } else if (elementsMimeType != null && elementsMimeType.isPlayable()) {
                addStream(parent, list, element, url);
            } else {
                Log.d(TAG, "fetch(): Ignoring URL: " + url);
            }
        }
    }

    @Nullable
    private String findLine(@NonNull final String[] lines, @NonNull String prefix) {
        for (String l : lines) {
            if (l.startsWith(prefix)) {
                return l;
            }
        }
        Log.w(TAG, "Error finding " + prefix);
        return null;
    }

    @Nullable
    private String[] findKV(@NonNull final String[] lines, @NonNull String prefix) {
        String l = findLine(lines, prefix);
        if (l == null) {
            return null;
        }

        String[] kv = l.split("=", 2);
        if (kv.length > 1) {
            return kv;
        }

        Log.w(TAG, "Error parsing key value pair: " + l);
        return null;
    }

    private void parsePls(@NonNull final Stream parent, final ArrayList<Stream> list, final Response response) throws IOException {
        Reader r = response.body().charStream();
        char[] arr = new char[8 * 1024];
        StringBuilder buffer = new StringBuilder();
        int numCharsRead;
        while ((numCharsRead = r.read(arr, 0, arr.length)) != -1) {
            buffer.append(arr, 0, numCharsRead);
        }
        r.close();

        String[] lines = buffer.toString().split("\n");
        int numberOfEntries = 0;
        String[] kv = findKV(lines, "NumberOfEntries=");
        if (kv != null) {
            numberOfEntries = Integer.parseInt(kv[1].trim());
        }

        for (int i = 1; i <= numberOfEntries; i++) {
            kv = findKV(lines, "File" + i + "=");
            if (kv != null) {
                Stream s = new Stream(parent, kv[1], null, new MimeType("audio/*"));

                kv = findKV(lines, "Title" + i + "=");
                if (kv != null) {
                    s.setTitle(kv[1]);
                }

                list.add(s);
            }
        }
    }

    private void addStream(@NonNull final Stream parent, @NonNull final ArrayList<Stream> list,
                           @NonNull final Element element, @NonNull final String url) throws IOException {
        Stream stream = new Stream(parent, url, element.text(), fetchMimeType(url));
        if (stream.isSupported()) {
            Log.d(TAG, "Adding stream: " + url);
            list.add(stream);
        } else {
            Log.w(TAG, "Ignoring invalid stream: " + url);
        }
    }

    @NonNull
    private String getUrlFromElement(final Element element) {
        String url = element.attr("abs:href");
        if (url.contains("#")) {
            url = url.substring(0, url.indexOf("#"));
        }
        return url;
    }

    @NonNull
    private List<Stream> queryStreamsByParent(@NonNull final ContentResolver cr,
                                              @NonNull final Stream parent) {
        return StreamsTable.getRows(cr.query(
                StreamsTable.CONTENT_URI,
                null,
                StreamsTable.FIELD_PARENT_ID + "=?",
                new String[]{String.valueOf(parent.getId())},
                null),
                true);
    }
}
