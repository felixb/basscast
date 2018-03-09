package de.ub0r.android.basscast;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultTransform;

import butterknife.BindDimen;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
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

        public InsertStreamsTask(Activity activity) {
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

    private static final String PREFS_FILE_CHROMECAST = "chromecast";

    private static final String PREFS_ROUTE_ID = "route_id";

    private static final String PREFS_SESSION_ID = "session_id";

    private static final int MAX_CONTROLS_TITLE_LENGTH_LARGE = 20;

    private static final int MAX_CONTROLS_TITLE_LENGTH_MEDIUM = 24;

    private static final double VOLUME_INCREMENT = 0.05;

    private boolean mWaitingForReconnect;

    private boolean mApplicationStarted;

    private MediaRouter.RouteInfo mRouteInfo;

    private String mRouteId;

    private String mSessionId;

    private CastContext mCastContext;
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
        }

        @Override
        public void onSessionResuming(Session session, String s) {

        }
    };
    private final Handler mUpdateControlsHandler = new Handler();
    private final ResultTransform<RemoteMediaClient.MediaChannelResult, Result> mResultCallback = new ResultTransform<RemoteMediaClient.MediaChannelResult, Result>() {
        @Nullable
        @Override
        public PendingResult<Result> onSuccess(@NonNull RemoteMediaClient.MediaChannelResult mediaChannelResult) {
            mUpdateControlsHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateControlViews(true);
                }
            });
            return null;
        }
    };

    private OnStateChangeListener mOnStateChangeListener;

    InterstitialAd mInterstitialAd;

    private StreamFetcher mFetcher;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    @BindView(R.id.fab)
    FloatingActionButton mFloatingActionButtonView;

    @BindView(R.id.controls)
    View mControlsLayout;

    @BindView(R.id.control_title)
    TextView mTitleView;

    @BindView(R.id.control_action_play)
    ImageButton mPlayView;

    @BindView(R.id.control_action_stop)
    ImageButton mStopView;

    @BindDimen(R.dimen.controls_height)
    int mControlsHeight;

    private Unbinder mUnbinder;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        mSessionManager = CastContext.getSharedInstance(this).getSessionManager();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        mUnbinder = ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
        mCastContext = CastContext.getSharedInstance(this);

        if (savedInstanceState == null) {
            insertDefaultStreams();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, BrowseFragment.getInstance(null))
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }

        SharedPreferences preferences = getSharedPreferences(PREFS_FILE_CHROMECAST, MODE_PRIVATE);
        mRouteId = preferences.getString(PREFS_ROUTE_ID, null);
        mSessionId = preferences.getString(PREFS_SESSION_ID, null);

        mFetcher = new StreamFetcher(this);

        mFloatingActionButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_INSERT, null, BrowseActivity.this,
                        EditStreamActivity.class));
            }
        });

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-1948477123608376/3415873285");
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();
            }
        });
        requestNewInterstitial();
    }

    @Override
    protected void onResume() {
        initCastSession();
        updateControlViews(false);
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

    @OnClick(R.id.control_action_stop)
    void onStopClick() {
        if (isConnected()) {
            mCastSession.getRemoteMediaClient().stop().then(mResultCallback);
        }
    }

    @OnClick(R.id.control_action_play)
    void onPlayClick() {
        if (isConnected()) {
            final RemoteMediaClient client = mCastSession.getRemoteMediaClient();
            if (client.isPlaying()) {
                client.pause().then(mResultCallback);
            } else {
                client.play().then(mResultCallback);
            }
        }
    }

    void setOnStateChangeListener(final OnStateChangeListener listener) {
        mOnStateChangeListener = listener;
    }

    @VisibleForTesting
    void setApplicationStarted(final boolean started) {
        mApplicationStarted = started;
        if (mOnStateChangeListener != null) {
            mOnStateChangeListener.onStateChange();
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
                if (mInterstitialAd.isLoaded()) {
                    mInterstitialAd.show();
                }
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
        remoteMediaClient.load(mediaInfo, new MediaLoadOptions.Builder().build()).then(mResultCallback);
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

    private void requestNewInterstitial() {
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        mInterstitialAd.loadAd(adRequest);
    }

    private void updateControlViews(final boolean showAnimations) {
        if (!isConnected()) {
            setControlsPosition(mControlsHeight, showAnimations);
            return;
        }

        MediaStatus status = mCastSession.getRemoteMediaClient().getMediaStatus();
        MediaInfo info = mCastSession.getRemoteMediaClient().getMediaInfo();
        boolean showControls = status != null && info != null && info.getMetadata() != null
                && (MediaStatus.PLAYER_STATE_PLAYING == status.getPlayerState()
                || MediaStatus.PLAYER_STATE_BUFFERING == status.getPlayerState()
                || MediaStatus.PLAYER_STATE_PAUSED == status.getPlayerState());

        if (showControls) {
            final String title = info.getMetadata().getString(MediaMetadata.KEY_TITLE);
            mTitleView.setText(title);
            //noinspection deprecation
            mTitleView.setTextAppearance(this, getControlsTitleStyle(title));
            if (MediaStatus.PLAYER_STATE_PAUSED == status.getPlayerState()) {
                mPlayView.setImageResource(R.drawable.ic_av_play_ripple);
                mStopView.setVisibility(View.VISIBLE);
            } else { // MediaStatus.PLAYER_STATE_PLAYING || MediaStatus.PLAYER_STATE_BUFFERING
                mPlayView.setImageResource(R.drawable.ic_av_pause_ripple);
                mStopView.setVisibility(View.VISIBLE);
            }
        }

        if (showControls) {
            setControlsPosition(0, showAnimations);
        } else {
            setControlsPosition(mControlsHeight, showAnimations);
        }
    }

    @StyleRes
    private int getControlsTitleStyle(@NonNull final String title) {
        final int length = title.length();
        if (length <= MAX_CONTROLS_TITLE_LENGTH_LARGE) {
            return android.R.style.TextAppearance_Large;
        }
        if (length <= MAX_CONTROLS_TITLE_LENGTH_MEDIUM) {
            return android.R.style.TextAppearance_Medium;
        }
        return android.R.style.TextAppearance_Small;
    }

    private void setControlsPosition(final float translationY, final boolean showAnimations) {
        if (showAnimations) {
            AnimatorSet anim = new AnimatorSet();
            anim
                    .play(ObjectAnimator.ofFloat(mControlsLayout, View.TRANSLATION_Y,
                            translationY))
                    .with(ObjectAnimator.ofFloat(mFloatingActionButtonView, View.TRANSLATION_Y,
                            translationY + mControlsHeight * -1));
            anim.start();
        } else {
            mControlsLayout.setTranslationY(translationY);
            mFloatingActionButtonView.setTranslationY(translationY + mControlsHeight * -1);
        }
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
