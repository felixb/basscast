package de.ub0r.android.basscast;

import com.google.android.gms.cast.MediaMetadata;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

/**
 * @author flx
 */
@SuppressLint("SetTextI18n")
public class EditStreamActivityTest extends ActivityInstrumentationTestCase2<EditStreamActivity> {

    private static final String TEST_URL = "http://example.org/test-stream";

    public EditStreamActivityTest() {
        super(EditStreamActivity.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getInstrumentation().getContext().getContentResolver()
                .delete(StreamsTable.CONTENT_URI, StreamsTable.FIELD_URL + "=?",
                        new String[]{TEST_URL});
    }

    public void testShowsStreamDetailsFromDatabase() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        assertEquals(stream.title, getActivity().mTitleView.getText().toString());
        assertEquals(stream.url, getActivity().mUrlView.getText().toString());
        assertEquals(stream.mimeType, getActivity().mMimeTypeView.getText().toString());
    }

    public void testShowsEmtpyStreamDetailsOnInsert() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        assertEquals("", getActivity().mTitleView.getText().toString());
        assertEquals("", getActivity().mUrlView.getText().toString());
        assertEquals("", getActivity().mMimeTypeView.getText().toString());
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
                activity.mMimeTypeView.setText("audio/*");
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
                activity.mMimeTypeView.setText("audio/*");
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
                activity.mMimeTypeView.setText("audio/*");
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotNull(activity.mUrlView.getError());
        assertNotInserted();
    }

    public void testInsertInvalidMimeType() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Stream w/ unsupported mime type");
                activity.mUrlView.setText(TEST_URL);
                activity.mMimeTypeView.setText("unsupported/type");
            }
        });

        invokeActionItem(R.id.action_save);

        assertNotNull(activity.mMimeTypeView.getError());
        assertNotInserted();
    }

    public void testInsertStream() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));
        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL);
                activity.mMimeTypeView.setText("video/*");
            }
        });

        invokeActionItem(R.id.action_save);

        Stream stream = assertInserted();
        assertEquals("Fancy test Stream!", stream.title);
        assertEquals(TEST_URL, stream.url);
        assertEquals("video/*", stream.mimeType);
        assertEquals(MediaMetadata.MEDIA_TYPE_MOVIE, stream.type);
    }

    public void testEditStream() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL);
                activity.mMimeTypeView.setText("audio/*");
            }
        });

        invokeActionItem(R.id.action_save);

        stream = assertInserted();

        assertEquals("Fancy test Stream!", stream.title);
        assertEquals(TEST_URL, stream.url);
        assertEquals("audio/*", stream.mimeType);
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK, stream.type);
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
        Stream stream = new Stream(TEST_URL, "Some fancy stream", "audio/mp3");
        Uri streamUri = getInstrumentation().getContext().getContentResolver()
                .insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(stream, false));
        stream.id = ContentUris.parseId(streamUri);
        return stream;
    }

    private Cursor queryTestStream() {
        return getInstrumentation().getContext().getContentResolver()
                .query(StreamsTable.CONTENT_URI, null, StreamsTable.FIELD_URL + "=?",
                        new String[]{TEST_URL}, null);
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
