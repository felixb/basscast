package de.ub0r.android.basscast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import de.ub0r.android.basscast.fetcher.FetchTask;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.tasks.StreamTask;

public class BrowseActivity extends AppCompatActivity {

    interface OnStateChangeListener {

        void onStateChange();
    }

    private static class InsertStreamsTask extends StreamTask {

        InsertStreamsTask(Activity activity) {
            super(activity, false);
        }

        @Override
        protected Void doInBackground(Stream... streams) {
            Log.i(TAG, "inserting " + streams.length + " default streams");
            mDao.insert(streams);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
            prefs.edit()
                    .putBoolean(PREFS_DEFAULT_STREAMS_INSERTED, true)
                    .apply();
            super.onPostExecute(aVoid);
        }
    }

    private static final String TAG = "BrowserActivity";

    private final static String PREFS_DEFAULT_STREAMS_INSERTED = "default_streams_inserted";

    private CastSession mCastSession;
    private SessionManager mSessionManager;
    private final SessionManagerListener mSessionManagerListener = new SessionManagerListener() {
        @Override
        public void onSessionStarting(Session session) {

        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            initCastSession();
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {

        }

        @Override
        public void onSessionEnding(Session session) {

        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            initCastSession();
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {

        }

        @Override
        public void onSessionSuspended(Session session, int i) {

        }

        @Override
        public void onSessionEnded(Session session, int error) {
            if (session == mCastSession) {
                mCastSession = null;
            }
            invalidateOptionsMenu();
        }

        @Override
        public void onSessionResuming(Session session, String s) {

        }
    };

    private StreamFetcher mFetcher;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    @BindView(R.id.fab)
    FloatingActionButton mFloatingActionButtonView;

    private Unbinder mUnbinder;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        mSessionManager = CastContext.getSharedInstance(this).getSessionManager();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        mUnbinder = ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        if (savedInstanceState == null) {
            insertDefaultStreams();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, BrowseFragment.getInstance(null))
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }

        mFetcher = new StreamFetcher(this);

        mFloatingActionButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_INSERT, null, BrowseActivity.this,
                        EditStreamActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        initCastSession();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSessionManager.removeSessionManagerListener(mSessionManagerListener);
        mCastSession = null;
    }

    @Override
    protected void onDestroy() {
        mUnbinder.unbind();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_browse, menu);

        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.action_cast);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("PrivateResource")
    public void setStreamInfo(final Stream stream) {
        if (stream == null) {
            mToolbar.setSubtitle(null);
            mToolbar.setNavigationIcon(null);
            mToolbar.setNavigationOnClickListener(null);
        } else {
            mToolbar.setSubtitle(stream.getTitle());
            mToolbar.setNavigationIcon(R.drawable.ic_action_arrow_back);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    Intent intent = new Intent(BrowseActivity.this, BrowseActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            });
        }
    }

    private void initCastSession() {
        mCastSession = mSessionManager.getCurrentCastSession();
        mSessionManager.addSessionManagerListener(mSessionManagerListener);
    }

    public void onStreamClick(final Stream stream) {
        if (stream.isPlayable()) {
            if (isConnected()) {
                castStream(stream);
            } else {
                Toast.makeText(this, R.string.error_not_connected, Toast.LENGTH_LONG).show();
            }
        } else {
            showStream(stream);
        }
    }

    private boolean isConnected() {
        return mCastSession != null && mCastSession.getCastDevice() != null;
    }

    void playStreamLocally(final Stream stream) {
        final int resId = R.string.playing_stream_on_this_device;
        Toast.makeText(this, getString(resId, stream.getTitle()), Toast.LENGTH_LONG).show();
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(stream.getUrl())));
    }

    void castStream(final Stream stream) {
        Toast.makeText(this,
                getString(R.string.casting_stream, stream.getTitle(), mCastSession.getCastDevice().getFriendlyName()),
                Toast.LENGTH_LONG).show();
        MediaInfo mediaInfo = stream.getMediaMetadata();
        RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        remoteMediaClient.load(mediaInfo, new MediaLoadOptions.Builder().build()).addStatusListener(new PendingResult.StatusListener() {
            @Override
            public void onComplete(Status status) {
                Log.i(TAG, "remoteMediaClient.load(): " + status.toString());
                if (!status.isSuccess()) {
                    Toast.makeText(BrowseActivity.this, R.string.error_loading_media, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showStream(final Stream parentStream) {
        BrowseFragment fragment = BrowseFragment.getInstance(parentStream);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
        new FetchTask(mFetcher, parentStream, fragment).execute((Void[]) null);
    }

    private void insertDefaultStreams() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(PREFS_DEFAULT_STREAMS_INSERTED, false)) {
            new InsertStreamsTask(this).execute(getDefaultStreams());
        }
    }

    private Stream[] getDefaultStreams() {
        String[] uris = getResources().getStringArray(R.array.default_streams);
        Stream[] streams = new Stream[uris.length];
        for (int i = 0; i < uris.length; i++) {
            streams[i] = new Stream(Uri.parse(uris[i]));
        }
        return streams;
    }
}
