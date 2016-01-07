package de.ub0r.android.basscast;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.TextView;

/**
 * @author flx
 */
public class InputError extends IllegalArgumentException {

    private final int mLocalizedMessage;

    public InputError(final String message, @StringRes final int localizedMessage) {
        super(message);
        mLocalizedMessage = localizedMessage;
    }

    public void show(@NonNull final Context context, @NonNull final TextView view) {
        view.setError(context.getString(mLocalizedMessage));
    }
}
