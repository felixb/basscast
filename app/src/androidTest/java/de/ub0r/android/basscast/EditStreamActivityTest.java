package de.ub0r.android.basscast;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import java.io.IOException;

import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * @author flx
 */
@SuppressLint("SetTextI18n")
public class EditStreamActivityTest extends ActivityInstrumentationTestCase2<EditStreamActivity> {

    private static final String TEST_URL = "http://example.org/test-stream";

    public static final String TEST_SELECTION = StreamsTable.FIELD_URL + " like '%example.org%' or "
            + StreamsTable.FIELD_URL + " like '%localhost%'";

    public EditStreamActivityTest() {
        super(EditStreamActivity.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getInstrumentation().getContext().getContentResolver()
                .delete(StreamsTable.CONTENT_URI, TEST_SELECTION, null);
    }

    public void testShowsStreamDetailsFromDatabase() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        assertEquals(stream.getTitle(), getActivity().mTitleView.getText().toString());
        assertEquals(stream.getUrl(), getActivity().mUrlView.getText().toString());
    }

    public void testShowsEmptyStreamDetailsOnInsert() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        assertEquals("", getActivity().mTitleView.getText().toString());
        assertEquals("", getActivity().mUrlView.getText().toString());
    }

    public void testLoadStreamFromNdefMessage() {
        final Stream stream = new Stream(TEST_URL, "nfc transmitted stream", new MimeType("text/html"));
        final Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        final NdefMessage msg = new NdefMessage(new NdefRecord[]{
                stream.toNdefRecord(),
                NdefRecord.createApplicationRecord(BuildConfig.APPLICATION_ID)});
        final Bundle extras = new Bundle();
        extras.putParcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{msg});
        intent.putExtras(extras);
        setActivityIntent(intent);

        assertEquals(TEST_URL, getActivity().mUrlView.getText().toString());
        assertEquals("nfc transmitted stream", getActivity().mTitleView.getText().toString());
    }

    public void testLoadStreamFromNdefMessageWithAARFirst() {
        final Stream stream = new Stream(TEST_URL, "nfc transmitted stream", new MimeType("text/html"));
        final Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        final NdefMessage msg = new NdefMessage(new NdefRecord[]{
                NdefRecord.createApplicationRecord(BuildConfig.APPLICATION_ID),
                stream.toNdefRecord()});
        final Bundle extras = new Bundle();
        extras.putParcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{msg});
        intent.putExtras(extras);
        setActivityIntent(intent);

        assertEquals(TEST_URL, getActivity().mUrlView.getText().toString());
        assertEquals("nfc transmitted stream", getActivity().mTitleView.getText().toString());
    }

    @SuppressWarnings("ConstantConditions")
    public void testDeletesStream() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));
        invokeActionItem(R.id.action_delete);

        assertEquals(0, getInstrumentation().getContext().getContentResolver()
                .query(stream.getUri(), null, null, null, null).getCount());
    }

    public void testInsertInvalidTitle() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("");
                activity.mUrlView.setText(TEST_URL);
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotNull(activity.mTitleView.getError());
        assertNotInserted();
    }

    public void testInsertEmptyUrl() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Stream w/ empty URL");
                activity.mUrlView.setText("");
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotNull(activity.mUrlView.getError());
        assertNotInserted();
    }

    public void testInsertInvalidUrl() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Stream w/ invalid URL");
                activity.mUrlView.setText("invalid url");
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotNull(activity.mUrlView.getError());
        assertNotInserted();
    }

    public void testInsertUnsupportedMimeType() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Stream w/ unsupported mime type");
                activity.mUrlView.setText(TEST_URL + "/cat.jpg");
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotInserted();
    }

    public void testInsertStream() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));
        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL + "/stream.wma");
            }
        });

        invokeActionItem(R.id.action_save);

        Stream stream = assertInserted();
        assertEquals("Fancy test Stream!", stream.getTitle());
        assertEquals(TEST_URL + "/stream.wma", stream.getUrl());
        assertEquals("audio/wma", stream.getMimeType());
    }

    public void testInsertStreamWithUnguessableMimeType() throws IOException {
        final MockWebServer server = new MockWebServer();

        server.enqueue(new MockResponse()
                .setBody("<html><body></body></html>")
                .setHeader("Content-Type", "audio/ogg"));
        server.start();

        final String url = server.url("/stream").toString();

        setActivityIntent(new Intent(Intent.ACTION_INSERT));
        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(url);
            }
        });

        invokeActionItem(R.id.action_save);

        // wait for async task. hiw would you test this?
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Stream stream = assertInserted();
        assertEquals("Fancy test Stream!", stream.getTitle());
        assertEquals(url, stream.getUrl());
        assertEquals("audio/ogg", stream.getMimeType());
    }

    public void testEditStream() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL + "/stream.mp4");
            }
        });

        invokeActionItem(R.id.action_save);

        stream = assertInserted();

        assertEquals("Fancy test Stream!", stream.getTitle());
        assertEquals(TEST_URL + "/stream.mp4", stream.getUrl());
        assertEquals("video/mp4", stream.getMimeType());
    }

    public void testEditStreamShowsDeleteMenuItem() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        assertNotNull("delete menu should be available in edit mode",
                getActivity().findViewById(R.id.action_delete));
    }

    public void testInsertStreamHidesDeleteMenuItem() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        assertNull("delete menu item should be gone in insert mode",
                getActivity().findViewById(R.id.action_delete));
    }

    private Stream insertTestStream() {
        Stream stream = new Stream(TEST_URL, "Some fancy stream", new MimeType("audio/mp3"));
        Uri streamUri = getInstrumentation().getContext().getContentResolver()
                .insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(stream, false));
        stream.setId(ContentUris.parseId(streamUri));
        return stream;
    }

    private Cursor queryTestStream() {
        return getInstrumentation().getContext().getContentResolver()
                .query(StreamsTable.CONTENT_URI, null,
                        TEST_SELECTION, null, null);
    }

    private void invokeActionItem(final int id) {
        final View menuView = getActivity().findViewById(id);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                menuView.requestFocus();
                menuView.performClick();
            }
        });
    }

    private void assertNotInserted() {
        Cursor cursor = queryTestStream();
        assertNotNull(cursor);
        assertFalse(cursor.moveToFirst());
        cursor.close();
    }

    private Stream assertInserted() {
        Cursor cursor = queryTestStream();
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        return StreamsTable.getRow(cursor, true);
    }
}
