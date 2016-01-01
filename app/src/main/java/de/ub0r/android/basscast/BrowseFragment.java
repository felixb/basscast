package de.ub0r.android.basscast;

import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class BrowseFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    class StreamHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private Stream mStream;

        @Bind(R.id.title)
        TextView mTitleView;

        @Bind(R.id.url)
        TextView mUrlView;

        public StreamHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(final View itemView) {
            getActivity().startActivity(
                    new Intent(Intent.ACTION_EDIT, mStream.getUri(), getContext(),
                            EditStreamActivity.class));
        }

        @OnClick(R.id.action_play)
        void onPlayClick() {
            getBrowseActivity().loadStream(mStream);
        }

        public void bindCursor(final Cursor cursor) {
            bind(StreamsTable.getRow(cursor, false));
        }

        private void bind(final Stream stream) {
            mStream = stream;
            mTitleView.setText(stream.title);
            mUrlView.setText(stream.url);
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

    private static final int LOADER_BROWSE = 1;

    @Bind(android.R.id.list)
    RecyclerView mRecyclerView;

    private StreamAdapter mAdapter;

    public BrowseFragment() {
    }

    public BrowseActivity getBrowseActivity() {
        return (BrowseActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
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
        getLoaderManager().restartLoader(LOADER_BROWSE, null, this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }


    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        return new CursorLoader(getActivity(), StreamsTable.CONTENT_URI, null,
                StreamsTable.FIELD_BASE_ID + "<0", null, StreamsTable.FIELD_TITLE + " ASC");
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        Log.d(TAG, "Showing new data set: " + data.getCount());
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }
}
