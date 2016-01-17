package de.ub0r.android.basscast;

import android.content.ContentValues;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import de.ub0r.android.basscast.model.MimeType;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.model.StreamsTable;

/**
 * @author flx
 */
public class BrowseFragmentTest extends ActivityInstrumentationTestCase2<BrowseActivity> {

    private static final String TEST_URL = "http://example.org/test-stream";

    public BrowseFragmentTest() {
        super(BrowseActivity.class);
    }

    @Override
    protected void tearDown() throws Exception {
        // reset streams
        final ContentValues values = new ContentValues();
        values.put(StreamsTable.FIELD_PARENT_ID, -1);
        getInstrumentation().getContext().getContentResolver()
                .update(
                        StreamsTable.CONTENT_URI,
                        values,
                        StreamsTable.FIELD_PARENT_ID + "=?",
                        new String[]{"9001"});

        // remove all test streams
        getInstrumentation().getContext().getContentResolver()
                .delete(StreamsTable.CONTENT_URI, StreamsTable.FIELD_URL + "=?",
                        new String[]{TEST_URL});

        super.tearDown();
    }

    public void testShowsEmptyView() {
        // hide all streams
        final ContentValues values = new ContentValues();
        values.put(StreamsTable.FIELD_PARENT_ID, 9001);
        getInstrumentation().getContext().getContentResolver()
                .update(
                        StreamsTable.CONTENT_URI,
                        values,
                        StreamsTable.FIELD_PARENT_ID + "=?",
                        new String[]{"-1"});

        BrowseActivity activity = getActivity();

        final View emptyView = activity.findViewById(android.R.id.empty);
        assertEquals(View.VISIBLE, emptyView.getVisibility());
    }

    public void testHidesEmptyView() {
        // add test stream
        Stream stream = new Stream(TEST_URL, "Some fancy stream", new MimeType("audio/mp3"));
        getInstrumentation().getContext().getContentResolver()
                .insert(StreamsTable.CONTENT_URI, StreamsTable.getContentValues(stream, false));

        BrowseActivity activity = getActivity();

        final View emptyView = activity.findViewById(android.R.id.empty);
        assertEquals(View.GONE, emptyView.getVisibility());
    }
}
