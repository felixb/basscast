package de.ub0r.android.basscast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.StringRes;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
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

import de.ub0r.android.basscast.model.Stream;

public class StreamController {

    private static final String TAG = "StreamController";
    private static final double PRELOAD_TIME = 10;

    private final Activity mActivity;
    private final SessionManager mSessionManager;
    private CastSession mCastSession;

    private final SessionManagerListener<Session> mSessionManagerListener = new SessionManagerListener<Session>() {
        @Override
        public void onSessionStarting(Session session) {

        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            initCastSession();
            mActivity.invalidateOptionsMenu();
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
            mActivity.invalidateOptionsMenu();
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
            mActivity.invalidateOptionsMenu();
        }

        @Override
        public void onSessionResuming(Session session, String s) {

        }
    };

    public StreamController(Activity activity) {
        mActivity = activity;
        mSessionManager = CastContext.getSharedInstance(activity).getSessionManager();
    }

    void initCastSession() {
        mCastSession = mSessionManager.getCurrentCastSession();
        mSessionManager.addSessionManagerListener(mSessionManagerListener);
    }

    void endCastSession() {
        mSessionManager.removeSessionManagerListener(mSessionManagerListener);
        mCastSession = null;
    }

    public boolean isConnected() {
        return mCastSession != null && mCastSession.getCastDevice() != null;
    }

    public MediaQueue getMediaQueue() {
        return mCastSession == null ? null : mCastSession.getRemoteMediaClient().getMediaQueue();
    }

    void playStreamLocally(final Stream stream) {
        showToast(mActivity.getString(R.string.playing_stream_on_this_device, stream.getTitle()));
        mActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(stream.getUrl())));
    }

    void castStream(final Stream stream) {
        showToast(mActivity.getString(R.string.casting_stream, stream.getTitle(), getDeviceFriendlyName()));
        final RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        final MediaInfo mediaInfo = stream.getMediaMetadata();
        load(remoteMediaClient, mediaInfo);
    }

    void queueStream(final Stream stream) {
        showToast(mActivity.getString(R.string.queue_stream, stream.getTitle(), getDeviceFriendlyName()));
        final RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        final MediaInfo mediaInfo = stream.getMediaMetadata();
        if (remoteMediaClient.getMediaQueue().getItemCount() == 0) {
            load(remoteMediaClient, mediaInfo);
        } else {
            append(remoteMediaClient, mediaInfo);
        }
    }

    void queueStreams(final List<Stream> streams) {
        showToast(mActivity.getString(R.string.queue_streams, streams.size(), getDeviceFriendlyName()));

        final RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        final MediaQueue mediaQueue = remoteMediaClient.getMediaQueue();

        final List<MediaQueueItem> queue = new ArrayList<>();
        for (Stream stream : streams) {
            queue.add(createQueueItem(stream.getMediaMetadata()));
        }

        final MediaQueueItem[] queueItems = queue.toArray(new MediaQueueItem[queue.size()]);
        if (mediaQueue.getItemCount() == 0) {
            remoteMediaClient.queueLoad(queueItems, 0, MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
                    .addStatusListener(new ResultLogger(mActivity, "queueLoad", R.string.error_queueing_media));
        } else {
            remoteMediaClient.queueInsertItems(queueItems, MediaQueueItem.INVALID_ITEM_ID, null)
                    .addStatusListener(new ResultLogger(mActivity, "queueInsertItems", R.string.error_queueing_media));
        }
    }

    void playFromQueue(final int itemtId) {
        getRemoteMediaClient().queueJumpToItem(itemtId, null)
                .addStatusListener(new ResultLogger(mActivity, "queueJumpToItem", String.valueOf(itemtId), R.string.error_play_from_queue));
    }

    void removeFromQueue(final int itemtId) {
        getRemoteMediaClient().queueRemoveItem(itemtId, null)
                .addStatusListener(new ResultLogger(mActivity, "queueRemoveItem", String.valueOf(itemtId), R.string.error_removing_from_queue));
    }

    void clearQueue() {
        final RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        final MediaQueue mediaQueue = remoteMediaClient.getMediaQueue();
        final int count = mediaQueue.getItemCount();
        if (count > 0) {
            int[] ids = new int[count];
            for (int i = 0; i < count; i++) {
                ids[i] = mediaQueue.itemIdAtIndex(i);
            }
            remoteMediaClient.queueRemoveItems(ids, null)
                    .addStatusListener(new ResultLogger(mActivity, "queueRemoveItems", R.string.error_clearing_queue));
        }
    }

    private void append(final RemoteMediaClient remoteMediaClient, final MediaInfo mediaInfo) {
        final MediaQueueItem queueItem = createQueueItem(mediaInfo);
        remoteMediaClient.queueAppendItem(queueItem, null)
                .addStatusListener(new ResultLogger(mActivity, "queueAppendItem", R.string.error_queueing_media));
    }

    private MediaQueueItem createQueueItem(final MediaInfo mediaInfo) {
        return new MediaQueueItem.Builder(mediaInfo)
                .setAutoplay(true)
                .setPreloadTime(PRELOAD_TIME)
                .build();
    }

    private void load(RemoteMediaClient remoteMediaClient, MediaInfo mediaInfo) {
        remoteMediaClient.load(mediaInfo, new MediaLoadOptions.Builder().build())
                .addStatusListener(new ResultLogger(mActivity, "load", R.string.error_loading_media));
    }

    private RemoteMediaClient getRemoteMediaClient() {
        return mCastSession.getRemoteMediaClient();
    }

    private String getDeviceFriendlyName() {
        return mCastSession.getCastDevice().getFriendlyName();
    }

    private void showToast(String msg) {
        Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show();
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
