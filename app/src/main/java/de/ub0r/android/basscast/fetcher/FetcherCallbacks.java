package de.ub0r.android.basscast.fetcher;

/**
 * Created by flx on 23.01.16.
 */
public interface FetcherCallbacks {

    void onFetchStarted();

    void onFetchFinished();

    void onFetchFailed();
}
