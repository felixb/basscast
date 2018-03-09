package de.ub0r.android.basscast.fetcher;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;

import de.ub0r.android.basscast.model.Stream;

/**
 * @author flx
 */
public class FetchTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "FetchTask";

    private final StreamFetcher mFetcher;

    private final Stream mParentStream;

    private final FetcherCallbacks mListener;

    public FetchTask(final StreamFetcher fetcher, final Stream parentStream,
                     final FetcherCallbacks listener) {
        mFetcher = fetcher;
        mParentStream = parentStream;
        mListener = listener;
    }

    @Override
    protected void onPreExecute() {
        if (mListener != null) {
            mListener.onFetchStarted();
        }
    }

    @Override
    protected Boolean doInBackground(final Void... voids) {
        try {
            final List<Stream> streams = mFetcher.fetch(mParentStream);
            mFetcher.insert(mParentStream, streams);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error fetching streams", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        if (mListener != null) {
            if (success) {
                mListener.onFetchFinished();
            } else {
                mListener.onFetchFailed();
            }
        }
    }
}
