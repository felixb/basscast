package de.ub0r.android.basscast;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
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
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import de.ub0r.android.basscast.fetcher.FetchTask;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.tasks.StreamTask;

public class BrowseActivity extends AppCompatActivity {

    public static final double PRELOAD_TIME = 10;

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

    private RemoteMediaClient.Callback mCallback = new RemoteMediaClient.Callback() {
        @Override
        public void onQueueStatusUpdated() {
            invalidateOptionsMenu();
        }
    };

    final View.OnClickListener mOnHomeClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            Intent intent = new Intent(BrowseActivity.this, BrowseActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    };

    private StreamFetcher mFetcher;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    @BindView(R.id.fab)
    FloatingActionButton mFloatingActionButton;

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
    }

    void setFloatingActionButtonModeAddStream() {
        mFloatingActionButton.setVisibility(View.VISIBLE);
        mFloatingActionButton.setImageResource(R.drawable.ic_content_add);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_INSERT, null, BrowseActivity.this,
                        EditStreamActivity.class));
            }
        });

    }

    void setFloatingActionButtonModeAddAllToQueue(final List<Stream> streams) {
        mFloatingActionButton.setVisibility(View.VISIBLE);
        mFloatingActionButton.setImageResource(R.drawable.ic_playlist_add);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueStreams(streams);
            }
        });
    }

    void setFloatingActionButtonDisabled() {
        mFloatingActionButton.setVisibility(View.GONE);
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
        getMenuInflater().inflate(R.menu.menu_browse_activity, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.action_cast);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        boolean showQueueItem = !(mCastSession == null || mCastSession.getRemoteMediaClient().getMediaQueue().getItemCount() == 0);
        menu.findItem(R.id.action_queue_show).setVisible(showQueueItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.action_queue_show:
                showQueue();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setSubtitle(final String title) {
        mToolbar.setSubtitle(title);
    }

    public void setHomeAsUp(final boolean enable) {
        if (enable) {
            mToolbar.setNavigationIcon(R.drawable.ic_action_arrow_back);
            mToolbar.setNavigationOnClickListener(mOnHomeClickListener);
        } else {
            mToolbar.setNavigationIcon(null);
            mToolbar.setNavigationOnClickListener(null);
        }
    }

    private void initCastSession() {
        mCastSession = mSessionManager.getCurrentCastSession();
        mSessionManager.addSessionManagerListener(mSessionManagerListener);
        if (mCastSession != null) {
            mCastSession.getRemoteMediaClient().registerCallback(mCallback);
        }
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

    boolean isConnected() {
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
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        final MediaInfo mediaInfo = stream.getMediaMetadata();
        load(remoteMediaClient, mediaInfo);
    }

    void queueStream(final Stream stream) {
        Toast.makeText(this,
                getString(R.string.queue_stream, stream.getTitle(), mCastSession.getCastDevice().getFriendlyName()),
                Toast.LENGTH_LONG).show();
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        final MediaInfo mediaInfo = stream.getMediaMetadata();
        if (remoteMediaClient.getMediaQueue().getItemCount() == 0) {
            load(remoteMediaClient, mediaInfo);
        } else {
            append(remoteMediaClient, mediaInfo);
        }
    }

    void queueStreams(final List<Stream> streams) {
        Toast.makeText(this,
                getString(R.string.queue_streams, streams.size(), mCastSession.getCastDevice().getFriendlyName()),
                Toast.LENGTH_LONG).show();

        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        final MediaQueue mediaQueue = remoteMediaClient.getMediaQueue();

        final List<MediaQueueItem> queue = new ArrayList<>();
        for (Stream stream : streams) {
            queue.add(createQueueItem(stream.getMediaMetadata()));
        }

        final MediaQueueItem[] queueItems = queue.toArray(new MediaQueueItem[queue.size()]);
        if (mediaQueue.getItemCount() == 0) {
            remoteMediaClient.queueLoad(queueItems, 0, MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
                    .addStatusListener(new ResultLogger(this, "queueLoad", R.string.error_queueing_media));
        } else {
            remoteMediaClient.queueInsertItems(queueItems, MediaQueueItem.INVALID_ITEM_ID, null)
                    .addStatusListener(new ResultLogger(this, "queueInsertItems", R.string.error_queueing_media));
        }
    }

    void playFromQueue(final int itemtId) {
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        remoteMediaClient.queueJumpToItem(itemtId, null)
                .addStatusListener(new ResultLogger(this, "queueJumpToItem", String.valueOf(itemtId), R.string.error_play_from_queue));
    }

    void removeFromQueue(final int itemtId) {
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        remoteMediaClient.queueRemoveItem(itemtId, null)
                .addStatusListener(new ResultLogger(this, "queueRemoveItem", String.valueOf(itemtId), R.string.error_removing_from_queue));
    }

    void clearQueue() {
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        final MediaQueue mediaQueue = remoteMediaClient.getMediaQueue();
        final int count = mediaQueue.getItemCount();
        if (count > 0) {
            int[] ids = new int[count];
            for (int i = 0; i < count; i++) {
                ids[i] = mediaQueue.itemIdAtIndex(i);
            }
            remoteMediaClient.queueRemoveItems(ids, null)
                    .addStatusListener(new ResultLogger(this, "queueRemoveItems", R.string.error_clearing_queue));
        }
    }

    private void append(final RemoteMediaClient remoteMediaClient, final MediaInfo mediaInfo) {
        final MediaQueueItem queueItem = createQueueItem(mediaInfo);
        remoteMediaClient.queueAppendItem(queueItem, null)
                .addStatusListener(new ResultLogger(this, "queueAppendItem", R.string.error_queueing_media));
    }

    private MediaQueueItem createQueueItem(final MediaInfo mediaInfo) {
        return new MediaQueueItem.Builder(mediaInfo)
                .setAutoplay(true)
                .setPreloadTime(PRELOAD_TIME)
                .build();
    }

    private void load(RemoteMediaClient remoteMediaClient, MediaInfo mediaInfo) {
        remoteMediaClient.load(mediaInfo, new MediaLoadOptions.Builder().build())
                .addStatusListener(new ResultLogger(this, "load", R.string.error_loading_media));
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

    private void showQueue() {
        QueueFragment fragment = QueueFragment.getInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
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

    private static class ResultLogger implements PendingResult.StatusListener {

        private final Activity mActivity;
        private final String mMethodName;
        private final String mParams;
        @StringRes
        private final int mErrorText;

        private ResultLogger(final Activity activity, final String methodName, final String params, @StringRes final int errorText) {
            mActivity = activity;
            mMethodName = methodName;
            mParams = params;
            mErrorText = errorText;
        }

        private ResultLogger(final Activity activity, final String methodName, @StringRes final int errorText) {
            this(activity, methodName, null, errorText);
        }

        @Override
        public void onComplete(final Status status) {
            Log.i(TAG, "remoteMediaClient." + mMethodName + "(" + getParamsString() + "): " + status.toString());
            mActivity.invalidateOptionsMenu();
            if (!status.isSuccess()) {
                Toast.makeText(mActivity, mErrorText, Toast.LENGTH_LONG).show();
            }
        }

        private String getParamsString() {
            if (mParams != null) {
                return mParams;
            } else {
                return "";
            }
        }
    }
}
