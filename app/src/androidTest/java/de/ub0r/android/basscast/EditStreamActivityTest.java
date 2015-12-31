package de.ub0r.android.basscast;

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
public class EditStreamActivityTest extends ActivityInstrumentationTestCase2<EditStreamActivity> {

    public static final String TEST_URL = "http://example.org/test-stream";

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
        assertEquals("" + stream.type, getActivity().mTypeView.getText().toString());
    }

    public void testShowsEmtpyStreamDetailsOnInsert() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));

        assertEquals("", getActivity().mTitleView.getText().toString());
        assertEquals("", getActivity().mUrlView.getText().toString());
        assertEquals("", getActivity().mMimeTypeView.getText().toString());
        assertEquals("0", getActivity().mTypeView.getText().toString());
    }

    @SuppressWarnings("ConstantConditions")
    public void testDeletesStream() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));
        invokeActionItem(R.id.action_delete);

        assertEquals(0, getInstrumentation().getContext().getContentResolver()
                .query(stream.getUri(), null, null, null, null).getCount());
    }

    @SuppressLint("SetTextI18n")
    public void testInsertStream() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));
        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL);
                activity.mMimeTypeView.setText("some/mimeType");
                activity.mTypeView.setText("5");
            }
        });

        invokeActionItem(R.id.action_save);

        Cursor cursor = getInstrumentation().getContext().getContentResolver()
                .query(StreamsTable.CONTENT_URI, null, StreamsTable.FIELD_URL + "=?",
                        new String[]{TEST_URL}, null);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        Stream stream = StreamsTable.getRow(cursor, true);

        assertEquals("Fancy test Stream!", stream.title);
        assertEquals(TEST_URL, stream.url);
        assertEquals("some/mimeType", stream.mimeType);
        assertEquals(5, stream.type);
    }

    @SuppressLint("SetTextI18n")
    public void testEditStream() {
        Stream stream = insertTestStream();
        setActivityIntent(new Intent(Intent.ACTION_EDIT, stream.getUri()));

        final EditStreamActivity activity = getActivity();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.mTitleView.setText("Fancy test Stream!");
                activity.mUrlView.setText(TEST_URL);
                activity.mMimeTypeView.setText("some/mimeType");
                activity.mTypeView.setText("5");
            }
        });

        invokeActionItem(R.id.action_save);

        Cursor cursor = getInstrumentation().getContext().getContentResolver()
                .query(StreamsTable.CONTENT_URI, null, StreamsTable.FIELD_URL + "=?",
                        new String[]{TEST_URL}, null);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        stream = StreamsTable.getRow(cursor, true);

        assertEquals("Fancy test Stream!", stream.title);
        assertEquals(TEST_URL, stream.url);
        assertEquals("some/mimeType", stream.mimeType);
        assertEquals(5, stream.type);
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
        Stream stream = new Stream(TEST_URL, "Some fancy stream", 1,
                "audio/mp3");
        Uri streamUri = getInstrumentation().getContext().getContentResolver()
                .insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(stream, false));
        stream.id = ContentUris.parseId(streamUri);
        return stream;
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
}
