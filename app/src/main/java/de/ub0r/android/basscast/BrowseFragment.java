package de.ub0r.android.basscast;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ub0r.android.basscast.fetcher.FetcherCallbacks;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class BrowseFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>, BrowseActivity.OnStateChangeListener,
        FetcherCallbacks {

    class StreamHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            PopupMenu.OnMenuItemClickListener {

        private Stream mStream;

        @Bind(R.id.title)
        TextView mTitleView;

        @Bind(R.id.url)
        TextView mUrlView;

        @Bind(R.id.action_context_menu)
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
                    StreamUtils.deleteStream(getContext(), mStream);
                    restartLoader();
                    return true;
                case R.id.action_play_locally:
                    getBrowseActivity().playStreamLocally(mStream);
                    return true;
                default:
                    return false;
            }
        }

        public void bindCursor(final Cursor cursor) {
            bind(StreamsTable.getRow(cursor, false));
        }

        private void bind(final Stream stream) {
            mStream = stream;
            mTitleView.setText(stream.getTitle());
            mUrlView.setText(stream.getUrl());
            mContextButton.setVisibility(stream.isBaseStream() || stream.isPlayable()
                    ? View.VISIBLE : View.GONE);
        }
    }

    private class StreamAdapter extends RecyclerViewCursorAdapter<StreamHolder> {

        private final LayoutInflater mLayoutInflater;

        public StreamAdapter(final Context context) {
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public StreamHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            View view = mLayoutInflater.inflate(R.layout.list_item_stream, parent, false);
            return new StreamHolder(view);
        }

        @Override
        public void onBindViewHolder(StreamHolder holder,
                                     Cursor cursor) {
            holder.bindCursor(cursor);
        }
    }

    private static final String TAG = "BrowseFragment";

    private static final String ARG_PARENT_STREAM = "PARENT_STREAM";

    private static final String ARG_IS_LOADING = "IS_LOADING";

    @Bind(android.R.id.list)
    RecyclerView mRecyclerView;

    @Bind(android.R.id.empty)
    View mEmptyView;

    @Bind(R.id.loading)
    View mLoadingView;

    private Stream mParentStream;

    private StreamAdapter mAdapter;

    private boolean mIsLoading;

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
        ButterKnife.bind(this, view);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new StreamAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        getBrowseActivity().setOnStateChangeListener(this);
        getBrowseActivity().setStreamInfo(mParentStream);
        restartLoader();
    }

    @Override
    public void onPause() {
        getBrowseActivity().setOnStateChangeListener(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }


    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putBoolean(ARG_IS_LOADING, mIsLoading);
        super.onSaveInstanceState(outState);
    }

    public void restartLoader() {
        if (getActivity() != null) {
            getLoaderManager().restartLoader(
                    mParentStream == null ? -1 : (int) mParentStream.getId(), null, this);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        return new CursorLoader(getActivity(), StreamsTable.CONTENT_URI, null,
                StreamsTable.FIELD_PARENT_ID + "=?", new String[]{String.valueOf(id)},
                StreamsTable.FIELD_TITLE + " ASC");
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        Log.d(TAG, "Showing new data set: " + data.getCount());
        mAdapter.swapCursor(data);
        updateViewsVisibility();
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
        updateViewsVisibility();
    }

    @Override
    public void onStateChange() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
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
        Cursor cursor = mAdapter.getCursor();
        boolean empty = cursor == null || cursor.getCount() == 0;
        mEmptyView.setVisibility(empty && !mIsLoading ? View.VISIBLE : View.GONE);
        mLoadingView.setVisibility(empty && mIsLoading ? View.VISIBLE : View.GONE);
    }
}
