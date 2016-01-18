package de.ub0r.android.basscast.fetcher;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;

/**
 * @author flx
 */
public class FetchMimeTypeTask extends AsyncTask<Void, Void, MimeType> {

    private static final String TAG = "FetchMimeTypeTask";

    private final StreamFetcher mFetcher;

    private final Stream mStream;

    private final FetcherCallbacks mListener;

    public FetchMimeTypeTask(final StreamFetcher fetcher, final Stream stream,
                             final FetcherCallbacks listener) {
        mFetcher = fetcher;
        mStream = stream;
        mListener = listener;
    }

    @Override
    protected void onPreExecute() {
        if (mListener != null) {
            mListener.onFetchStarted();
        }
    }

    @Override
    protected MimeType doInBackground(final Void... voids) {
        try {
            return mFetcher.fetchMimeType(mStream.getUrl());
        } catch (IOException e) {
            Log.e(TAG, "Error fetching streams", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(final MimeType mimeType) {
        if (mimeType == null) {
            if (mListener != null) {
                mListener.onFetchFailed();
            }
        } else {
            if (mListener != null) {
                mStream.setMimeType(mimeType);
                mListener.onFetchFinished();
            }
        }
    }
}
