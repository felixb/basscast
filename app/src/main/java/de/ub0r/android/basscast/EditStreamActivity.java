package de.ub0r.android.basscast;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.ub0r.android.basscast.fetcher.FetchMimeTypeTask;
import de.ub0r.android.basscast.fetcher.FetcherCallbacks;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

public class EditStreamActivity extends AppCompatActivity implements
        NfcAdapter.CreateNdefMessageCallback {

    private static final String TAG = "EditStreamActivity";

    private static final String ARG_STREAM = "STREAM";

    @Bind(R.id.title)
    EditText mTitleView;

    @Bind(R.id.url)
    EditText mUrlView;

    private Stream mStream;

    private StreamFetcher mFetcher;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_stream);
        ButterKnife.bind(this);

        mFetcher = new StreamFetcher(this);

        final String action = getIntent().getAction();
        final Uri data = getIntent().getData();
        if (savedInstanceState != null) {
            mStream = new Stream(savedInstanceState.getBundle(ARG_STREAM));
        } else if (Intent.ACTION_EDIT.equals(action) && data != null) {
            mStream = fetchStream(data);
        } else if (Intent.ACTION_INSERT.equals(action)) {
            mStream = new Stream();
        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            try {
                parseNdefMessage(getIntent());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unable to load stream from NDEF message", e);
                mStream = new Stream();
            }
        } else {
            throw new IllegalArgumentException("Illegal ACTION: " + action);
        }

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcAdapter.setNdefPushMessageCallback(this, this);
        }

        restoreViewsFromStream();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putBundle(ARG_STREAM, mStream.toBundle());
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_stream, menu);
        if (mStream.getId() < 0) {
            menu.removeItem(R.id.action_delete);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                saveAndFinish();
                return true;
            case R.id.action_delete:
                StreamUtils.deleteStream(this, mStream);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public NdefMessage createNdefMessage(final NfcEvent nfcEvent) {
        storeStreamFromViews();
        return getNdefMessage();
    }

    @NonNull
    private NdefMessage getNdefMessage() {
        return new NdefMessage(new NdefRecord[]{
                mStream.toNdefRecord(),
                NdefRecord.createApplicationRecord(BuildConfig.APPLICATION_ID)});
    }

    private void saveAndFinish() {
        try {
            saveStream();
            finish();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Saving stream failed", e);
        }
    }

    private void restoreViewsFromStream() {
        mTitleView.setText(mStream.getTitle());
        mUrlView.setText(mStream.getUrl());
    }

    private void storeStreamFromViews() {
        mStream.setTitle(mTitleView.getText().toString());
        final String newUrl = mUrlView.getText().toString();
        if (!newUrl.equals(mStream.getUrl())) {
            mStream.setUrl(newUrl);
            mStream.setMimeType((MimeType) null);
        }
        checkStream();
    }

    private void checkStream() {
        boolean valid = true;

        if (TextUtils.isEmpty(mStream.getTitle())) {
            Log.w(TAG, "Missing title");
            mTitleView.setError(getString(R.string.missing_mandatory_parameter));
            valid = false;
        }

        String url = mStream.getUrl();
        if (TextUtils.isEmpty(url)) {
            Log.w(TAG, "Missing url");
            mUrlView.setError(getString(R.string.missing_mandatory_parameter));
            valid = false;
        } else {
            final Uri uri = Uri.parse(url);
            if (!"http".equals(uri.getScheme())
                    && !"https".equals(uri.getScheme())) {
                Log.w(TAG, "Invalid url");
                mUrlView.setError(getString(R.string.invalid_url));
                valid = false;
            } else if (mStream.getMimeType() == null) {
                fetchMimeType();
            }
        }

        if (mStream.getMimeType() == null) {
            Log.w(TAG, "Null mimeType");
            mUrlView.setError(getString(R.string.error_fetching_mime_type));
            valid = false;
        } else if (!mStream.isSupported()) {
            Log.w(TAG, "Unsupported mimeType: " + mStream.getMimeType());
            mUrlView.setError(getString(R.string.unsupported_mime_type));
            valid = false;
        }

        if (!valid) {
            throw new IllegalArgumentException("Input checks failed");
        }
    }

    private void fetchMimeType() {
        String url = mStream.getUrl();
        mStream.setMimeType(MimeType.guessMimeType(url));

        if (mStream.getMimeType() == null) {
            Log.d(TAG, "Need to fetch mimeType with AsyncTask");

            // fetch unguessable
            new FetchMimeTypeTask(mFetcher, mStream, new FetcherCallbacks() {

                private AlertDialog mDialog;

                @Override
                public void onFetchStarted() {
                    Log.d(TAG, "Start fetching mimeType");
                    mDialog = new AlertDialog.Builder(EditStreamActivity.this)
                            .setMessage(R.string.loading)
                            .setCancelable(true)
                            .create();
                    mDialog.show();
                }

                @Override
                public void onFetchFinished() {
                    Log.i(TAG, "Fetched mimeType: " + mStream.getMimeType());
                    saveAndFinish();
                    try {
                        mDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed closing dialog when finishing activity", e);
                    }
                }

                @Override
                public void onFetchFailed() {
                    Log.e(TAG, "Failed fetching mimeType");
                    mDialog.dismiss();
                    Toast.makeText(EditStreamActivity.this,
                            R.string.error_fetching_mime_type, Toast.LENGTH_LONG).show();
                }
            }).execute((Void) null);
            throw new IllegalArgumentException("Fetching mime type in AsyncTask");
        }
    }

    private void saveStream() {
        storeStreamFromViews();
        ContentValues contentValues = StreamsTable.getContentValues(mStream, false);
        if (mStream.getId() < 0) {
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

    private void parseNdefMessage(final Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        Log.d(TAG, "#msgs: " + rawMsgs.length);
        for (Parcelable rawMsg : rawMsgs) {
            NdefMessage msg = (NdefMessage) rawMsg;
            Log.d(TAG, "#records: " + msg.getRecords().length);
            for (NdefRecord record : msg.getRecords()) {
                try {
                    mStream = new Stream(record);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "NDEF record is not a stream", e);
                }
            }
        }
        throw new IllegalArgumentException("Invalid NDEF message");
    }
}
