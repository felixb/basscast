package de.ub0r.android.basscast;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

import butterknife.Bind;
import butterknife.BindDimen;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ub0r.android.basscast.fetcher.FetchTask;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.Stream;

public class BrowseActivity extends AppCompatActivity {

    interface OnStateChangeListener {

        void onStateChange();
    }

    private class MediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(final MediaRouter router, final MediaRouter.RouteInfo info) {
            mRouteInfo = info;
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());
            final SharedPreferences prefs = getSharedPreferences(PREFS_FILE_CHROMECAST,
                    MODE_PRIVATE);
            prefs.edit().putString(PREFS_ROUTE_ID, info.getId()).apply();
            launchReceiver();
        }

        @Override
        public void onRouteUnselected(final MediaRouter router, final MediaRouter.RouteInfo info) {
            teardown();
        }

        @Override
        public void onRouteAdded(final MediaRouter router, final MediaRouter.RouteInfo route) {
            Log.d(TAG, "Route added: " + route.getName());
            if (mRouteId != null && mRouteInfo == null && mRouteId.equals(route.getId())) {
                restoreRoute();
            }
        }
    }

    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle connectionHint) {
            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                // TODO reconnec channels
            } else if (mSessionId != null && mRouteInfo != null) {
                joinSession();
            } else {
                newSession();
            }

            updateControlViews(true);

        }

        @Override
        public void onConnectionSuspended(int cause) {
            mWaitingForReconnect = true;
        }

        private void joinSession() {
            try {
                Cast.CastApi
                        .joinApplication(mApiClient,
                                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                                mSessionId)
                        .setResultCallback(
                                new ResultCallback<Cast.ApplicationConnectionResult>() {
                                    @Override
                                    public void onResult(
                                            @NonNull final Cast.ApplicationConnectionResult result) {
                                        Status status = result.getStatus();
                                        if (status.isSuccess()) {
                                            Log.i(TAG, "Joined session: " + mRouteInfo.getName());
                                            setApplicationStarted(true);
                                            mMediaRouter.selectRoute(mRouteInfo);
                                            connectRemoteMediaPlayer();
                                        } else {
                                            teardown();
                                        }
                                    }
                                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to join application", e);
            }
        }

        private void newSession() {
            try {
                Cast.CastApi
                        .launchApplication(mApiClient,
                                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                        .setResultCallback(
                                new ResultCallback<Cast.ApplicationConnectionResult>() {
                                    @Override
                                    public void onResult(
                                            @NonNull final Cast.ApplicationConnectionResult result) {
                                        Status status = result.getStatus();
                                        if (status.isSuccess()) {
                                            setApplicationStarted(true);
                                            mSessionId = result.getSessionId();
                                            getSharedPreferences(PREFS_FILE_CHROMECAST,
                                                    MODE_PRIVATE).edit()
                                                    .putString(PREFS_SESSION_ID, mSessionId)
                                                    .apply();
                                            Log.i(TAG, "Application launched: " + result
                                                    .getApplicationMetadata().getName());
                                            connectRemoteMediaPlayer();
                                        } else {
                                            teardown();
                                        }
                                    }
                                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }
    }

    private void connectRemoteMediaPlayer() {
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mRemoteMediaPlayer.getNamespace(),
                    mRemoteMediaPlayer);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }
        mRemoteMediaPlayer
                .requestStatus(mApiClient)
                .setResultCallback(
                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(
                                    @NonNull final RemoteMediaPlayer.MediaChannelResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    Log.e(TAG, "Failed to request status.");
                                }
                            }
                        });
    }

    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(@NonNull final ConnectionResult result) {
            teardown();
        }
    }

    private static final String TAG = "BrowserActivity";

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

    private GoogleApiClient mApiClient;

    private OnStateChangeListener mOnStateChangeListener;

    InterstitialAd mInterstitialAd;

    private StreamFetcher mFetcher;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;

    private Cast.Listener mCastClientListener = new Cast.Listener() {
        @Override
        public void onApplicationStatusChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onApplicationStatusChanged: "
                        + Cast.CastApi.getApplicationStatus(mApiClient));
            }
        }

        @Override
        public void onVolumeChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int errorCode) {
            teardown();
        }
    };

    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks = new ConnectionCallbacks();

    private GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener
            = new ConnectionFailedListener();

    private MediaRouter mMediaRouter;

    private MediaRouteSelector mMediaRouteSelector;

    private MediaRouterCallback mMediaRouterCallback = new MediaRouterCallback();

    private RemoteMediaPlayer mRemoteMediaPlayer;

    private CastDevice mSelectedDevice;

    @Bind(R.id.fab)
    FloatingActionButton mFloatingActionButtonView;

    @Bind(R.id.controls)
    View mControlsLayout;

    @Bind(R.id.control_title)
    TextView mTitleView;

    @Bind(R.id.control_action_play)
    ImageButton mPlayView;

    @Bind(R.id.control_action_stop)
    ImageButton mStopView;

    @BindDimen(R.dimen.controls_height)
    int mControlsHeight;

    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        if (savedInstanceState == null) {
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

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(
                        CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();

        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener(
                new RemoteMediaPlayer.OnStatusUpdatedListener() {
                    @Override
                    public void onStatusUpdated() {
                        MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                        if (mediaStatus == null) {
                            Log.w(TAG, "mediaStatus == null");
                        } else {
                            boolean isPlaying = mediaStatus.getPlayerState() ==
                                    MediaStatus.PLAYER_STATE_PLAYING;
                            Log.d(TAG, "RemoteMediaPlayer is playing: " + isPlaying);
                        }
                    }
                });

        mRemoteMediaPlayer.setOnMetadataUpdatedListener(
                new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                    @Override
                    public void onMetadataUpdated() {
                        MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();
                        if (mediaInfo == null) {
                            Log.d(TAG, "RemoteMediaPlayer metadata updated: null");
                        } else {
                            Log.d(TAG, "RemoteMediaPlayer metadata updated: " + mediaInfo);
                        }
                        updateControlViews(true);
                    }
                });

        restoreRoute();
        updateControlViews(false);

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
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_browse, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.action_cast);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        final int action = event.getAction();
        final int keyCode = event.getKeyCode();
        //AudioManager mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        //mAudioManager.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME,
        //        AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mApiClient != null && mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume < 1.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient,
                                        Math.min(currentVolume + VOLUME_INCREMENT, 1.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        return true;
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mApiClient != null && mRemoteMediaPlayer != null) {
                        double currentVolume = Cast.CastApi.getVolume(mApiClient);
                        if (currentVolume > 0.0) {
                            try {
                                Cast.CastApi.setVolume(mApiClient,
                                        Math.max(currentVolume - VOLUME_INCREMENT, 0.0));
                            } catch (Exception e) {
                                Log.e(TAG, "unable to set volume", e);
                            }
                        }
                    } else {
                        return true;
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @OnClick(R.id.control_action_stop)
    void onStopClick() {
        mRemoteMediaPlayer.stop(mApiClient).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                    @Override
                    public void onResult(
                            @NonNull final RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                        updateControlViews(true);
                    }
                });
    }

    @OnClick(R.id.control_action_play)
    void onPlayClick() {
        MediaStatus status = mRemoteMediaPlayer.getMediaStatus();

        if (MediaStatus.PLAYER_STATE_PAUSED == status.getPlayerState()) {
            mRemoteMediaPlayer.play(mApiClient).setResultCallback(
                    new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(
                                @NonNull final RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                            updateControlViews(true);
                        }
                    });
        } else { // MediaStatus.PLAYER_STATE_PLAYING || MediaStatus.PLAYER_STATE_BUFFERING
            mRemoteMediaPlayer.pause(mApiClient).setResultCallback(
                    new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(
                                @NonNull final RemoteMediaPlayer.MediaChannelResult mediaChannelResult) {
                            updateControlViews(true);
                        }
                    });
        }
    }

    void setOnStateChangeListener(final OnStateChangeListener listener) {
        mOnStateChangeListener = listener;
    }

    boolean isApplicationStarted() {
        return mApplicationStarted;
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
            mToolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
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

    public void onStreamClick(final Stream stream) {
        if (stream.isPlayable()) {
            if (mApplicationStarted) {
                castStream(stream);
                if (mInterstitialAd.isLoaded()) {
                    mInterstitialAd.show();
                }
            } else {
                playStreamLocally(stream, false);
            }
        } else {
            showStream(stream);
        }
    }

    void playStreamLocally(final Stream stream, final boolean intentionally) {
        final int resId = intentionally
                ? R.string.playing_stream_on_this_device
                : R.string.playing_stream_on_this_device_not_connected;
        Toast.makeText(this, getString(resId, stream.getTitle()), Toast.LENGTH_LONG).show();
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(stream.getUrl())));
    }

    void castStream(final Stream stream) {
        Toast.makeText(this,
                getString(R.string.casting_stream, stream.getTitle(), mSelectedDevice.getFriendlyName()),
                Toast.LENGTH_LONG).show();
        MediaInfo mediaInfo = stream.getMediaMetadata();

        try {
            mRemoteMediaPlayer
                    .load(mApiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(
                                @NonNull final RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                Log.d(TAG, "Media loaded successfully");
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Problem occurred with media during loading", e);
            Toast.makeText(this, R.string.error_loading_media, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
            Toast.makeText(this, R.string.error_opening_media, Toast.LENGTH_LONG).show();
        }
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
        MediaStatus status = mRemoteMediaPlayer.getMediaStatus();
        MediaInfo info = mRemoteMediaPlayer.getMediaInfo();
        boolean showControls = isApplicationStarted()
                && status != null && info != null && info.getMetadata() != null
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

    private void restoreRoute() {
        if (mRouteId != null) {
            Log.d(TAG, "Restore route");
            for (MediaRouter.RouteInfo route : mMediaRouter.getRoutes()) {
                if (mRouteId.equals(route.getId())) {
                    Log.i(TAG, "Found existing route, restore session: " + route.getName());
                    mRouteInfo = route;
                    mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
                    launchReceiver();
                    return;
                }
            }
        }
    }

    private void launchReceiver() {
        Cast.CastOptions apiOptions = new Cast.CastOptions
                .Builder(mSelectedDevice, mCastClientListener)
                .build();

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptions)
                .addConnectionCallbacks(mConnectionCallbacks)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();

        mApiClient.connect();
    }

    private void teardown() {
        Log.d(TAG, "Tear down cast connection");
        if (mApiClient != null) {
            if (isApplicationStarted()) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    mApiClient.disconnect();
                }
                setApplicationStarted(false);
            }
            mApiClient = null;
        }
        mRouteInfo = null;
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;

        getSharedPreferences(PREFS_FILE_CHROMECAST, MODE_PRIVATE).edit()
                .remove(PREFS_ROUTE_ID)
                .remove(PREFS_SESSION_ID)
                .apply();

        updateControlViews(true);
    }
}
