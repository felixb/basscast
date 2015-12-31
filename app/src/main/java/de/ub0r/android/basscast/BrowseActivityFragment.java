package de.ub0r.android.basscast;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class BrowseActivityFragment extends Fragment {

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

    @Bind(android.R.id.list)
    RecyclerView mRecyclerView;

    private StreamAdapter mAdapter;

    public BrowseActivityFragment() {
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
        mAdapter.swapCursor(getActivity().getContentResolver().query(
                StreamsTable.CONTENT_URI, null, null, null, null));
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }
}
