package de.ub0r.android.basscast;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.ub0r.android.basscast.fetcher.FetcherCallbacks;
import de.ub0r.android.basscast.model.AppDatabase;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamDao;
import de.ub0r.android.basscast.tasks.DeleteStreamTask;

public class BrowseFragment extends Fragment implements FetcherCallbacks {

    class StreamHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            PopupMenu.OnMenuItemClickListener {

        private Stream mStream;
        private TextView mTitleView;
        private TextView mUrlView;
        private ImageButton mContextButton;

        StreamHolder(final View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            mTitleView = itemView.findViewById(R.id.title);
            mUrlView = itemView.findViewById(R.id.url);
            mContextButton = itemView.findViewById(R.id.action_context_menu);
            itemView.findViewById(R.id.action_context_menu)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            onPopupMenuClick(view);
                        }
                    });
        }

        @Override
        public void onClick(final View itemView) {
            getBrowseActivity().onStreamClick(mStream);
        }

        void onPopupMenuClick(final View view) {
            final PopupMenu popup = new PopupMenu(getContext(), view);
            popup.inflate(R.menu.menu_browse_context);
            popup.setOnMenuItemClickListener(this);
            final Menu menu = popup.getMenu();
            if (!mStream.isBaseStream()) {
                menu.removeItem(R.id.action_edit);
                menu.removeItem(R.id.action_delete);
            }
            if (!mStream.isPlayable()) {
                menu.removeItem(R.id.action_play_locally);
            }
            if (!getStreamController().isConnected()) {
                menu.removeItem(R.id.action_queue_append);
            }
            popup.show();
        }

        @Override
        public boolean onMenuItemClick(final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_edit:
                    StreamUtils.editStream(getContext(), mStream);
                    return true;
                case R.id.action_delete:
                    new DeleteStreamTask(getActivity(), false).execute(mStream);
                    return true;
                case R.id.action_play_locally:
                    getStreamController().playStreamLocally(mStream);
                    return true;
                case R.id.action_queue_append:
                    getStreamController().queueStream(mStream);
                    return true;
                default:
                    return false;
            }
        }

        private void bind(final Stream stream) {
            mStream = stream;
            mTitleView.setText(stream.getTitle());
            mUrlView.setText(stream.getDecodedUrl());
            mContextButton.setVisibility(stream.isBaseStream() || stream.isPlayable()
                    ? View.VISIBLE : View.GONE);
        }
    }

    private class StreamAdapter extends RecyclerView.Adapter<StreamHolder> {

        private final LayoutInflater mLayoutInflater;
        private List<Stream> mStreams;

        StreamAdapter(final Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            mStreams = new ArrayList<>();
        }

        public void swapStreams(final List<Stream> streams) {
            mStreams = streams == null ? new ArrayList<Stream>() : streams;
            this.notifyDataSetChanged();
        }

        @NonNull
        @Override
        public StreamHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View view = mLayoutInflater.inflate(R.layout.list_item_stream, parent, false);
            return new StreamHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StreamHolder holder, int position) {
            holder.bind(mStreams.get(position));
        }

        @Override
        public int getItemCount() {
            return mStreams.size();
        }

        public List<Stream> getStreams() {
            return mStreams;
        }

        public List<Stream> getPlayableStreams() {
            List<Stream> streams = new ArrayList<>();
            for (Stream stream : mStreams) {
                if (stream.isPlayable()) {
                    streams.add(stream);
                }
            }
            return streams;
        }
    }

    private static final String ARG_PARENT_STREAM = "PARENT_STREAM";
    private static final String ARG_IS_LOADING = "IS_LOADING";
    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private View mLoadingView;
    private Stream mParentStream;
    private StreamAdapter mAdapter;
    private boolean mIsLoading;
    private LiveData<List<Stream>> mData;

    public static BrowseFragment getInstance(final Stream parentStream) {
        BrowseFragment f = new BrowseFragment();
        if (parentStream != null) {
            Bundle args = new Bundle();
            args.putBundle(ARG_PARENT_STREAM, parentStream.toBundle());
            f.setArguments(args);
        }
        return f;
    }

    public BrowseFragment() {
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
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_PARENT_STREAM)) {
            mParentStream = new Stream(args.getBundle(ARG_PARENT_STREAM));
        }
        if (savedInstanceState != null) {
            mIsLoading = savedInstanceState.getBoolean(ARG_IS_LOADING);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse, container, false);
        mRecyclerView = view.findViewById(android.R.id.list);
        mEmptyView = view.findViewById(android.R.id.empty);
        mLoadingView = view.findViewById(R.id.loading);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new StreamAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        final BrowseActivity activity = getBrowseActivity();
        final boolean hasParentStream = mParentStream != null;
        activity.setSubtitle(hasParentStream ? mParentStream.getTitle() : null);
        activity.setHomeAsUp(hasParentStream);
        updateFloatingActionButtonMode();
        restartLoader();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putBoolean(ARG_IS_LOADING, mIsLoading);
        super.onSaveInstanceState(outState);
    }

    public void restartLoader() {
        if (getActivity() != null) {
            final StreamDao dao = AppDatabase.Builder.getInstance(getActivity()).streamDao();
            final long parentId = mParentStream == null ? -1 : mParentStream.getId();
            mData = dao.getWithParentSync(parentId);
            mData.observe(this, new Observer<List<Stream>>() {
                @Override
                public void onChanged(@Nullable List<Stream> streams) {
                    mAdapter.swapStreams(streams);
                    updateViewsVisibility();
                    updateFloatingActionButtonMode();
                }
            });
        }
    }

    @Override
    public void onFetchStarted() {
        mIsLoading = true;
        updateViewsVisibility();
    }

    @Override
    public void onFetchFinished() {
        mIsLoading = false;
        restartLoader();
    }

    @Override
    public void onFetchFailed() {
        mIsLoading = false;
        updateViewsVisibility();
        Toast.makeText(getContext(), R.string.error_fetching_stream, Toast.LENGTH_LONG).show();
        // TODO alow user to refetch. -> snackbar?
    }

    private void updateViewsVisibility() {
        if (mEmptyView == null) {
            return;
        }
        boolean empty = mData == null || mData.getValue() == null || mData.getValue().size() == 0;
        mEmptyView.setVisibility(empty && !mIsLoading ? View.VISIBLE : View.GONE);
        mLoadingView.setVisibility(empty && mIsLoading ? View.VISIBLE : View.GONE);
    }

    private void updateFloatingActionButtonMode() {
        final BrowseActivity activity = getBrowseActivity();
        if (mParentStream == null) {
            activity.setFloatingActionButtonModeAddStream();
        } else if (!activity.getStreamController().isConnected()) {
            activity.setFloatingActionButtonDisabled();
        } else {
            final List<Stream> playableStreams = mAdapter.getPlayableStreams();
            if (playableStreams.size() > 0) {
                activity.setFloatingActionButtonModeAddAllToQueue(playableStreams);
            } else {
                activity.setFloatingActionButtonDisabled();
            }
        }
    }
}
