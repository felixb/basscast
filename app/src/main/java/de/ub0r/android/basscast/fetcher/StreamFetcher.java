package de.ub0r.android.basscast.fetcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StreamFetcher {

    private static final String TAG = "StreamFetcher";

    private static final HashMap<String, String> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put(".html", "text/html");
        EXTENSIONS.put(".htm", "text/html");
        EXTENSIONS.put(".php", "text/html");
        EXTENSIONS.put(".jsp", "text/html");
        EXTENSIONS.put(".asp", "text/html");
        EXTENSIONS.put(".mp3", "audio/mp3");
        EXTENSIONS.put(".mp4", "video/mp4");
        EXTENSIONS.put(".png", "image/png");
        EXTENSIONS.put(".gif", "image/gif");
        EXTENSIONS.put(".jpg", "image/jpeg");
        EXTENSIONS.put(".jpeg", "image/jpeg");
    }

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
                if (oldStream.url.equals(newStream.url)) {
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
            cr.delete(oldStream.getUri(), null, null);
        }

        // insert/update new streams
        for (Stream newStream : streams) {
            boolean found = false;
            for (Stream oldStream : existingStreams) {
                if (oldStream.url.equals(newStream.url)) {
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

    public String fetchMimeType(final String url) throws IOException {
        String mimeType = guessMimeType(url);
        if (mimeType != null) {
            return mimeType;
        }

        final Response response = mHttpClient.newCall(new Request.Builder()
                .head()
                .url(url)
                .build()).execute();
        mimeType = response.header("Content-Type");
        if (mimeType == null) {
            return null;
        }

        return mimeType.split(";", 2)[0].trim().toLowerCase();
    }

    public List<Stream> fetch(@NonNull final Stream parent) throws IOException {
        final ArrayList<Stream> list = new ArrayList<>();

        final Response response = mHttpClient.newCall(new Request.Builder()
                .url(parent.url)
                .build()).execute();
        String mimeType = response.header("Content-Type");
        if (mimeType != null && "text/html".equals(mimeType)) {
            final Document doc = Jsoup.parse(response.body().string(), parent.url);
            Elements elements = doc.select("a[href]");
            for (Element element : elements) {
                String url = element.attr("abs:href");
                if (url.startsWith(parent.url)) {
                    try {
                        list.add(new Stream(parent, url, element.text(), fetchMimeType(url)));
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Ignoring invalid stream: " + e);
                    }
                }
            }
        }
        return list;
    }

    @Nullable
    private String guessMimeType(@NonNull final String url) {
        final String lowerUrl = url.toLowerCase();
        for (String ext : EXTENSIONS.keySet()) {
            if (lowerUrl.endsWith(ext)) {
                return EXTENSIONS.get(ext);
            }
        }
        return null;
    }

    @NonNull
    private List<Stream> queryStreamsByParent(@NonNull final ContentResolver cr,
                                              @NonNull final Stream parent) {
        return StreamsTable.getRows(cr.query(
                        StreamsTable.CONTENT_URI,
                        null,
                        StreamsTable.FIELD_PARENT_ID + "=?",
                        new String[]{String.valueOf(parent.id)},
                        null),
                true);
    }
}
