package de.ub0r.android.basscast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.Toast;

import java.io.IOException;

import de.ub0r.android.basscast.model.Stream;

public class BrowseActivity extends AppCompatActivity {

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
                reconnectChannels();
            } else if (mSessionId != null && mRouteInfo != null) {
                joinSession();
            } else {
                newSession();
            }
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
                                            mApplicationStarted = true;
                                            mMediaRouter.selectRoute(mRouteInfo);
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
                                            mApplicationStarted = true;
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

    void loadStream(final Stream stream) {
        Toast.makeText(this, "Loading stream: " + stream.title, Toast.LENGTH_LONG).show();
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
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
        }
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

    private static final double VOLUME_INCREMENT = 0.05;

    private boolean mWaitingForReconnect;

    private boolean mApplicationStarted;

    private MediaRouter.RouteInfo mRouteInfo;

    private String mRouteId;

    private String mSessionId;

    private GoogleApiClient mApiClient;

    private Cast.Listener mCastClientListener = new Cast.Listener() {
        @Override
        public void onApplicationStatusChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onApplicationStatusChanged: "
                        + Cast.CastApi.getApplicationStatus(mApiClient));
                // TODO
            }
        }

        @Override
        public void onVolumeChanged() {
            if (mApiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(mApiClient));
                // TODO
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences preferences = getSharedPreferences(PREFS_FILE_CHROMECAST, MODE_PRIVATE);
        mRouteId = preferences.getString(PREFS_ROUTE_ID, null);
        mSessionId = preferences.getString(PREFS_SESSION_ID, null);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
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
                        if (mediaInfo != null) {
                            Log.d(TAG, "Remote media player metadata updated: " + mediaInfo);
                        }
                    }
                });

        restoreRoute();
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
                        return false;
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
                        return false;
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
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
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    Cast.CastApi.stopApplication(mApiClient, mSessionId);
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
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
    }

    private void reconnectChannels() {
        // TODO
    }
}
