package de.ub0r.android.basscast;

import android.content.Context;
import android.content.Intent;

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
}
