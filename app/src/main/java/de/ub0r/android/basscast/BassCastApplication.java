package de.ub0r.android.basscast;

import com.google.android.gms.cast.MediaMetadata;

import android.app.Application;
import android.content.ContentResolver;

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
                    "http://download.media.tagesschau.de/video/2015/1222/TV-20151222-2020-4401.webm.h264.mp4",
                    "Tagesschau",
                    MediaMetadata.MEDIA_TYPE_MOVIE,
                    "video/mp4"
            ), false));

            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://radioeins.de/stream",
                    "radio 1",
                    MediaMetadata.MEDIA_TYPE_MUSIC_TRACK,
                    "audio/mp3"
            ), false));

            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(new Stream(
                    "http://amsterdam2.shouthost.com.streams.bassdrive.com:8000",
                    "bassdrive",
                    MediaMetadata.MEDIA_TYPE_MUSIC_TRACK,
                    "audio/mpeg"
            ), false));
        }
    }
}
