package de.ub0r.android.basscast;

import android.content.Context;
import android.content.Intent;

import de.ub0r.android.basscast.model.Stream;

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
}
