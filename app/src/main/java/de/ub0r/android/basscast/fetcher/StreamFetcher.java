package de.ub0r.android.basscast.fetcher;

import android.content.Context;
import android.support.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.ub0r.android.basscast.model.Stream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StreamFetcher {

    private static final HashMap<String, String> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put(".html", "text/html");
        EXTENSIONS.put(".htm", "text/html");
        EXTENSIONS.put(".php", "text/html");
        EXTENSIONS.put(".jsp", "text/html");
        EXTENSIONS.put(".asp", "text/html");
        EXTENSIONS.put(".mp3", "audio/mp3");
        EXTENSIONS.put(".mp4", "video/mp4");
    }

    private final Context mContext;
    private final OkHttpClient mHttpClient;


    public StreamFetcher(final Context context) {
        mContext = context;
        mHttpClient = new OkHttpClient().newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public void fetchAndInsertRecursively(final Stream baseStream) {
        // TODO
    }

    public void insert(final List<Stream> streams) {
        // TODO
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

        return mimeType.split(";", 2)[0].trim();
    }

    public List<Stream> fetch(final Stream baseStream) throws IOException {
        final ArrayList<Stream> list = new ArrayList<>();

        final Response response = mHttpClient.newCall(new Request.Builder().url(baseStream.url).build()).execute();
        String mimeType = response.header("Content-Type");
        if (mimeType != null && "text/html".equals(mimeType)) {
            final Document doc = Jsoup.parse(response.body().string(), baseStream.url);
            Elements elements = doc.select("a[href]");
            for (Element e : elements) {
                String url = e.attr("abs:href");
                if (url.startsWith(baseStream.url)) {
                    list.add(new Stream(url, e.text(), fetchMimeType(url)));
                }
            }
        }
        return list;
    }

    @Nullable
    private String guessMimeType(String url) {
        for (String ext : EXTENSIONS.keySet()) {
            if (url.endsWith(ext)) {
                return EXTENSIONS.get(ext);
            }
        }
        return null;
    }
}
