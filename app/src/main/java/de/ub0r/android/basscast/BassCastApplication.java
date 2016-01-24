package de.ub0r.android.basscast;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * @author flx
 */
public class BassCastApplication extends Application {

    private final static String PREFS_DEFAULT_STREAMS_INSERTED = "default_streams_inserted";

    @Override
    public void onCreate() {
        super.onCreate();
        insertDefaultStreams();
    }

    private void insertDefaultStreams() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(PREFS_DEFAULT_STREAMS_INSERTED, false)) {
            StreamUtils.insertDefaultStreams(this);
            prefs.edit()
                    .putBoolean(PREFS_DEFAULT_STREAMS_INSERTED, true)
                    .apply();
        }
    }
}
