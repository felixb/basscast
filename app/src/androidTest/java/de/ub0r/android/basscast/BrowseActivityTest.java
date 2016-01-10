package de.ub0r.android.basscast;

import org.mockito.Mockito;

import android.test.ActivityInstrumentationTestCase2;

import de.ub0r.android.basscast.model.Stream;

/**
 * @author flx
 */
public class BrowseActivityTest extends ActivityInstrumentationTestCase2<BrowseActivity> {

    public BrowseActivityTest() {
        super(BrowseActivity.class);
    }

    public void testCallingOnStateChangeListener() {
        BrowseActivity activity = getActivity();
        assertFalse(activity.isApplicationStarted());

        activity.setOnStateChangeListener(null);
        activity.setApplicationStarted(true);
        assertTrue(activity.isApplicationStarted());

        BrowseActivity.OnStateChangeListener mock = Mockito
                .mock(BrowseActivity.OnStateChangeListener.class);
        activity.setOnStateChangeListener(mock);

        activity.setApplicationStarted(false);
        assertFalse(activity.isApplicationStarted());
        Mockito.verify(mock).onStateChange();
    }

    public void testSetStreamInfo() {
        final BrowseActivity activity = getActivity();
        assertNotNull(activity.mToolbar);
        assertNull(activity.mToolbar.getSubtitle());

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Stream stream = new Stream("http://example.org/", "example stream", "text/html");
                activity.setStreamInfo(stream);
            }
        });
        assertEquals("example stream", activity.mToolbar.getSubtitle());

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.setStreamInfo(null);
            }
        });
        assertNull(activity.mToolbar.getSubtitle());
    }
}
