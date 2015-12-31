package de.ub0r.android.basscast;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;

/**
 * Source: http://www.blogc.at/2015/10/13/recyclerview-adapters-part-2-recyclerview-cursor-adapter/
 */
public abstract class RecyclerViewCursorAdapter<VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {

    private Cursor mCursor;

    public void swapCursor(final Cursor cursor) {
        mCursor = cursor;
        this.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    public Cursor getItem(final int position) {
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.moveToPosition(position);
        }

        return mCursor;
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public final void onBindViewHolder(final VH holder, final int position) {
        final Cursor cursor = this.getItem(position);
        this.onBindViewHolder(holder, cursor);
    }

    public abstract void onBindViewHolder(final VH holder, final Cursor cursor);
}