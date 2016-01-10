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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ub0r.android.basscast.fetcher.FetchTask;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class BrowseFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>, BrowseActivity.OnStateChangeListener,
        FetchTask.FetcherCallbacks {

    class StreamHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private Stream mStream;

        @Bind(R.id.title)
        TextView mTitleView;

        @Bind(R.id.url)
        TextView mUrlView;

        @Bind(R.id.action_play)
        ImageButton mPlayButton;

        public StreamHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(final View itemView) {
            getBrowseActivity().onStreamClick(mStream);
        }

        @OnClick(R.id.action_play)
        void onPlayClick() {
            getBrowseActivity().playStream(mStream);
        }

        public void bindCursor(final Cursor cursor) {
            bind(StreamsTable.getRow(cursor, false));
        }

        private void bind(final Stream stream) {
            mStream = stream;
            mTitleView.setText(stream.title);
            mUrlView.setText(stream.url);
            mPlayButton.setVisibility(isApplicationStarted() && mStream.isMedia() ?
                    View.VISIBLE : View.GONE);
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

    @Bind(android.R.id.list)
    RecyclerView mRecyclerView;

    @Bind(android.R.id.empty)
    View mEmptyView;

    private Stream mParentStream;

    private StreamAdapter mAdapter;

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

    public void restartLoader() {
        if (getActivity() != null) {
            getLoaderManager().restartLoader(
                    mParentStream == null ? -1 : (int) mParentStream.id, null, this);
        }
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
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
        setEmptyViewVisibility();
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
        setEmptyViewVisibility();
    }

    @Override
    public void onStateChange() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFetchStarted() {
        // TODO update UI if empty list
    }

    @Override
    public void onFetchFinished() {
        restartLoader();
    }

    @Override
    public void onFetchFailed() {
        // TODO
    }

    private boolean isApplicationStarted() {
        return getBrowseActivity().isApplicationStarted();
    }

    private void setEmptyViewVisibility() {
        if (mEmptyView == null) {
            return;
        }
        Cursor cursor = mAdapter.getCursor();
        mEmptyView
                .setVisibility(cursor == null || cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
