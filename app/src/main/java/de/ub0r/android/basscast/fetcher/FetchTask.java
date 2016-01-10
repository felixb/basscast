package de.ub0r.android.basscast.fetcher;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;

import de.ub0r.android.basscast.model.Stream;

/**
 * @author flx
 */
public class FetchTask extends AsyncTask<Void, Void, List<Stream>> {

    public interface FetcherCallbacks {

        void onFetchStarted();

        void onFetchFinished();

        void onFetchFailed();
    }

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
    protected List<Stream> doInBackground(final Void... voids) {
        try {
            return mFetcher.fetch(mParentStream);
        } catch (IOException e) {
            Log.e(TAG, "Error fetching streams", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(final List<Stream> streams) {
        if (streams == null) {
            if (mListener != null) {
                mListener.onFetchFailed();
            }
        } else {
            mFetcher.insert(mParentStream, streams);
            if (mListener != null) {
                mListener.onFetchFinished();
            }
        }
    }
}
