package de.ub0r.android.basscast;

import android.app.Application;
import android.content.ContentResolver;

import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

/**
 * @author flx
 */
public class BassCastApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initDummyStreams();
    }

    private void initDummyStreams() {
        ContentResolver cr = getContentResolver();
        if (cr.query(StreamsTable.CONTENT_URI, null, null, null, null).getCount() == 0) {
            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://www.tagesschau.de/sendung/tagesschau/index.html",
                    "Tagesschau",
                    new MimeType("video/mp4")
            ), false));

            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://radioeins.de/stream",
                    "radio 1",
                    new MimeType("audio/mp3")
            ), false));

            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://amsterdam2.shouthost.com.streams.bassdrive.com:8000/;",
                    "bassdrive",
                    new MimeType("audio/mpeg")
            ), false));
            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://archives.bassdrivearchive.com",
                    "bassdrive archives",
                    new MimeType("text/html")
            ), false));
        }
    }
}
