package de.ub0r.android.basscast;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import de.ub0r.android.basscast.fetcher.FetcherCallbacks;
import de.ub0r.android.basscast.model.AppDatabase;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamDao;
import de.ub0r.android.basscast.tasks.DeleteStreamTask;

public class BrowseFragment extends Fragment implements FetcherCallbacks {

    class StreamHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            PopupMenu.OnMenuItemClickListener {

        private Stream mStream;

        @BindView(R.id.title)
        TextView mTitleView;

        @BindView(R.id.url)
        TextView mUrlView;

        @BindView(R.id.action_context_menu)
        ImageButton mContextButton;

        public StreamHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(final View itemView) {
            getBrowseActivity().onStreamClick(mStream);
        }

        @OnClick(R.id.action_context_menu)
        void onPopupMenuClick(final View view) {
            PopupMenu menu = new PopupMenu(getContext(), view);
            menu.inflate(R.menu.menu_browse_context);
            menu.setOnMenuItemClickListener(this);
            if (!mStream.isBaseStream()) {
                menu.getMenu().removeItem(R.id.action_edit);
                menu.getMenu().removeItem(R.id.action_delete);
            }
            if (!mStream.isPlayable()) {
                menu.getMenu().removeItem(R.id.action_play_locally);
            }
            menu.show();
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
                    getBrowseActivity().playStreamLocally(mStream);
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

        public StreamAdapter(final Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            mStreams = new ArrayList<>();
        }

        public StreamAdapter(final Context context, final List<Stream> streams) {
            mLayoutInflater = LayoutInflater.from(context);
            mStreams = streams;
        }

        public void swapStreams(final List<Stream> streams) {
            mStreams = streams == null ? new ArrayList<Stream>() : streams;
            this.notifyDataSetChanged();
        }

        @Override
        public StreamHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            View view = mLayoutInflater.inflate(R.layout.list_item_stream, parent, false);
            return new StreamHolder(view);
        }

        @Override
        public void onBindViewHolder(StreamHolder holder, int position) {
            holder.bind(mStreams.get(position));
        }

        @Override
        public int getItemCount() {
            return mStreams.size();
        }
    }

    private static final String TAG = "BrowseFragment";

    private static final String ARG_PARENT_STREAM = "PARENT_STREAM";

    private static final String ARG_IS_LOADING = "IS_LOADING";

    @BindView(android.R.id.list)
    RecyclerView mRecyclerView;

    @BindView(android.R.id.empty)
    View mEmptyView;

    @BindView(R.id.loading)
    View mLoadingView;

    private Stream mParentStream;

    private StreamAdapter mAdapter;

    private boolean mIsLoading;

    private LiveData<List<Stream>> mData;

    private Unbinder mUnbinder;

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
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse, container, false);
        mUnbinder = ButterKnife.bind(this, view);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new StreamAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        getBrowseActivity().setStreamInfo(mParentStream);
        restartLoader();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mUnbinder.unbind();
        super.onDestroyView();
    }


    @Override
    public void onSaveInstanceState(final Bundle outState) {
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
}
