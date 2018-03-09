package de.ub0r.android.basscast.tasks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;

import de.ub0r.android.basscast.model.AppDatabase;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamDao;

public abstract class StreamTask extends AsyncTask<Stream, Void, Void> {

    @SuppressLint("StaticFieldLeak")
    protected Activity mActivity;
    protected final StreamDao mDao;
    private boolean mFinishOnPostExecute;

    public StreamTask(final Activity activity, final boolean finishOnPostExecute) {
        mActivity = activity;
        mDao = AppDatabase.Builder.getInstance(activity).streamDao();
        mFinishOnPostExecute = finishOnPostExecute;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (mFinishOnPostExecute) {
            mActivity.finish();
        }
        mActivity = null;
    }
}

