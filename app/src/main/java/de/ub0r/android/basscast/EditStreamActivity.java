package de.ub0r.android.basscast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import de.ub0r.android.basscast.fetcher.FetchMimeTypeTask;
import de.ub0r.android.basscast.fetcher.FetcherCallbacks;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.AppDatabase;
import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamDao;
import de.ub0r.android.basscast.tasks.DeleteStreamTask;
import de.ub0r.android.basscast.tasks.StreamTask;

public class EditStreamActivity extends AppCompatActivity implements
        NfcAdapter.CreateNdefMessageCallback, FetcherCallbacks {

    static class FetchStreamTask extends AsyncTask<Uri, Void, Void> {
        @SuppressLint("StaticFieldLeak")
        private EditStreamActivity mActivity;
        private final StreamDao mDao;

        FetchStreamTask(final EditStreamActivity activity) {
            mActivity = activity;
            mDao = AppDatabase.Builder.getInstance(activity).streamDao();
        }


        @Override
        protected Void doInBackground(Uri... uris) {
            final long streamId = ContentUris.parseId(uris[0]);

            final Stream stream = mDao.get(streamId);
            if (stream != null) {
                mActivity.mStream = stream;
                return null;
            }
            throw new IllegalArgumentException("Unable to fetch Stream with id " + streamId);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mActivity.restoreViewsFromStream();
            mActivity = null;
        }
    }

    static class SaveStreamTask extends StreamTask {
        SaveStreamTask(Activity activity) {
            super(activity, true);
        }

        @Override
        protected Void doInBackground(Stream... streams) {
            final Stream stream = streams[0];
            if (stream.getId() <= 0) {
                mDao.insert(stream);
            } else {
                mDao.update(stream);
            }
            return null;
        }
    }

    private static final String TAG = "EditStreamActivity";
    private static final String ARG_STREAM = "STREAM";
    private EditText mTitleView;
    private EditText mUrlView;
    private Stream mStream;
    private StreamFetcher mFetcher;
    private AlertDialog mFetchingDialog;

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_stream);
        mTitleView = findViewById(R.id.title);
        mUrlView = findViewById(R.id.url);

        mFetcher = new StreamFetcher(this);

        final String action = getIntent().getAction();
        final Uri data = getIntent().getData();
        if (savedInstanceState != null) {
            mStream = new Stream(savedInstanceState.getBundle(ARG_STREAM));
        } else if (Intent.ACTION_VIEW.equals(action) && data != null) {
            mUrlView.setText(data.toString());
        } else if (Intent.ACTION_EDIT.equals(action) && data != null) {
            new FetchStreamTask(this).execute(data);
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
        if (mStream != null && mStream.getId() <= 0) {
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
                new DeleteStreamTask(this, true).execute(mStream);
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

    @SuppressLint("StaticFieldLeak")
    private void saveAndFinish() {
        try {
            storeStreamFromViews();
        } catch (IllegalArgumentException e) {
            // wait for retry on callback :x
            return;
        }

        new SaveStreamTask(this).execute(mStream);
    }

    private void restoreViewsFromStream() {
        if (mStream != null) {
            mTitleView.setText(mStream.getTitle());
            mUrlView.setText(mStream.getUrl());
        }
    }

    private void storeStreamFromViews() {
        mStream.setTitle(mTitleView.getText().toString().trim());
        final String newUrl = mUrlView.getText().toString().trim();
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
            new FetchMimeTypeTask(mFetcher, mStream, this).execute((Void) null);
            throw new IllegalArgumentException("Fetching mime type in AsyncTask");
        }
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

    @Override
    public void onFetchStarted() {
        Log.d(TAG, "Start fetching mimeType");
        mFetchingDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.loading)
                .setCancelable(true)
                .create();
        mFetchingDialog.show();
    }

    @Override
    public void onFetchFinished() {
        Log.i(TAG, "Fetched mimeType: " + mStream.getMimeType());
        saveAndFinish();
        try {
            mFetchingDialog.dismiss();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed closing dialog when finishing activity", e);
        }
    }

    @Override
    public void onFetchFailed() {
        Log.e(TAG, "Failed fetching mimeType");
        mFetchingDialog.dismiss();
        Toast.makeText(this, R.string.error_fetching_mime_type, Toast.LENGTH_LONG).show();

        // let's ask the user
        new AlertDialog.Builder(this)
                .setMessage(R.string.audio_or_video)
                .setCancelable(true)
                .setNeutralButton(R.string.video, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        mStream.setMimeType(new MimeType("video/*"));
                        saveAndFinish();
                    }
                })
                .setPositiveButton(R.string.audio, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        mStream.setMimeType(new MimeType("audio/*"));
                        saveAndFinish();
                    }
                })
                .show();
    }
}
