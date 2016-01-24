package de.ub0r.android.basscast;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

/**
 * @author flx
 */
public class StreamUtils {

    private StreamUtils() {
        // hide constructor
    }

    public static void editStream(final Context context, final Stream stream) {
        context.startActivity(new Intent(Intent.ACTION_EDIT, stream.getUri(),
                context, EditStreamActivity.class));
    }

    public static void deleteStream(final Context context, final Stream stream) {
        final String id = String.valueOf(stream.getId());
        context.getContentResolver().delete(StreamsTable.CONTENT_URI,
                StreamsTable.FIELD__ID + "=? or "
                        + StreamsTable.FIELD_BREADCRUMBS + " like '%'||?||'%'",
                new String[]{id, id});
    }

    public static void insertDefaultStreams(final Context context) {
        insertStreams(context.getContentResolver(), getDefaultStreams(context));
    }

    private static Stream[] getDefaultStreams(final Context context) {
        String[] uris = context.getResources().getStringArray(R.array.default_streams);
        Stream[] streams = new Stream[uris.length];
        for (int i = 0; i < uris.length; i++) {
            streams[i] = new Stream(Uri.parse(uris[i]));
        }
        return streams;
    }

    private static void insertStreams(final ContentResolver cr, final Stream[] streams) {
        for (Stream stream : streams) {
            cr.insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(stream, false));
        }
    }
}
