package de.ub0r.android.basscast;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.MediaQueueRecyclerViewAdapter;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

public class QueueFragment extends Fragment {

    class QueueHolder extends RecyclerView.ViewHolder implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {

        @BindView(R.id.title)
        TextView mTitleView;

        private MediaQueueItem mItem;

        QueueHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        public void bind(MediaQueueItem item) {
            mItem = item;
            if (item == null) {
                mTitleView.setText(null);
            } else {
                mTitleView.setText(item.getMedia().getMetadata().getString(MediaMetadata.KEY_TITLE));
            }
        }

        @Override
        public void onClick(final View view) {
            if (mItem != null) {
                getStreamController().playFromQueue(mItem.getItemId());
            }
        }

        @OnClick(R.id.action_context_menu)
        void onPopupMenuClick(final View view) {
            final PopupMenu popup = new PopupMenu(getContext(), view);
            popup.inflate(R.menu.menu_queue_context);
            popup.setOnMenuItemClickListener(this);
            popup.show();
        }

        @Override
        public boolean onMenuItemClick(final MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_delete:
                    if (mItem != null) {
                        getStreamController().removeFromQueue(mItem.getItemId());
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private class QueueAdapter extends MediaQueueRecyclerViewAdapter<QueueHolder> {

        QueueAdapter(MediaQueue mediaQueue) {
            super(mediaQueue);
        }

        @NonNull
        @Override
        public QueueHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View view = getLayoutInflater().inflate(R.layout.list_item_queue, parent, false);
            return new QueueHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final QueueHolder holder, final int position) {
            final MediaQueueItem item = getItem(position);
            holder.bind(item);
        }

    }

    private static final String TAG = "QueueFragment";

    @BindView(android.R.id.list)
    RecyclerView mRecyclerView;

    private QueueAdapter mAdapter;
    private Unbinder mUnbinder;

    public static QueueFragment getInstance() {
        return new QueueFragment();
    }

    public QueueFragment() {
    }

    public BrowseActivity getBrowseActivity() {
        return (BrowseActivity) getActivity();
    }

    private StreamController getStreamController() {
        return getBrowseActivity().getStreamController();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_queue, container, false);
        mUnbinder = ButterKnife.bind(this, view);

        final Context context = getContext();
        final RemoteMediaClient remoteMediaClient = CastContext.getSharedInstance(context).getSessionManager().getCurrentCastSession().getRemoteMediaClient();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mAdapter = new QueueAdapter(remoteMediaClient.getMediaQueue());
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        final BrowseActivity activity = getBrowseActivity();
        activity.setSubtitle(getString(R.string.queue));
        activity.setHomeAsUp(true);
    }

    @Override
    public void onDestroyView() {
        mUnbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_queue_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_queue_clear:
                getStreamController().clearQueue();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
