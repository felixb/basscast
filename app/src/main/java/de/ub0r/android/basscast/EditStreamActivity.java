package de.ub0r.android.basscast;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class EditStreamActivity extends AppCompatActivity {

    private static final String TAG = "EditStreamActivity";

    private static final String ARG_STREAM = "STREAM";

    @Bind(R.id.title)
    EditText mTitleView;

    @Bind(R.id.url)
    EditText mUrlView;

    @Bind(R.id.mimeType)
    EditText mMimeTypeView;

    private Stream mStream;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_stream);
        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            mStream = new Stream(savedInstanceState.getBundle(ARG_STREAM));
        } else if (Intent.ACTION_EDIT.equals(getIntent().getAction())
                && getIntent().getData() != null) {
            mStream = fetchStream(getIntent().getData());
        } else if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            mStream = new Stream();
        } else {
            throw new IllegalArgumentException("Illegal ACTION: " + getIntent().getAction());
        }

        restoreViewsFromStream();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState,
            final PersistableBundle outPersistentState) {
        outState.putBundle(ARG_STREAM, mStream.toBundle());
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_stream, menu);
        if (mStream.id < 0) {
            menu.removeItem(R.id.action_delete);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                try {
                    saveStream();
                    finish();
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "saving stream failed");
                }
                return true;
            case R.id.action_delete:
                StreamUtils.deleteStream(this, mStream);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void restoreViewsFromStream() {
        mTitleView.setText(mStream.title);
        mUrlView.setText(mStream.url);
        mMimeTypeView.setText(mStream.mimeType);
    }

    private void storeStreamFromViews() {
        boolean valid = checkFields();
        mStream.title = mTitleView.getText().toString();
        mStream.url = mUrlView.getText().toString();
        mStream.mimeType = mMimeTypeView.getText().toString();

        try {
            mStream.parseMimeType();
        } catch (InputError e) {
            e.show(this, mMimeTypeView);
            throw e;
        }
        if (!valid) {
            throw new IllegalArgumentException("input checks failed");
        }
    }

    private boolean checkFields() {
        boolean result = true;
        if (TextUtils.isEmpty(mTitleView.getText())) {
            mTitleView.setError(getString(R.string.missing_mandatory_parameter));
            result = false;
        }

        if (TextUtils.isEmpty(mUrlView.getText())) {
            mUrlView.setError(getString(R.string.missing_mandatory_parameter));
            result = false;
        } else {
            final Uri uri = Uri.parse(mUrlView.getText().toString());
            if (!"http".equals(uri.getScheme())
                    && !"https".equals(uri.getScheme())) {
                mUrlView.setError(getString(R.string.invalid_url));
                result = false;
            }
        }

        return result;
    }

    private void saveStream() {
        storeStreamFromViews();
        ContentValues contentValues = StreamsTable.getContentValues(mStream, false);
        if (mStream.id < 0) {
            getContentResolver().insert(StreamsTable.CONTENT_URI, contentValues);
        } else {
            getContentResolver().update(mStream.getUri(), contentValues, null, null);
        }
    }

    private Stream fetchStream(final Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                return StreamsTable.getRow(cursor, true);
            }
            throw new IllegalArgumentException("Unknown Stream: " + uri);
        }
        throw new IllegalArgumentException("Unable to fetch Stream");
    }
}
