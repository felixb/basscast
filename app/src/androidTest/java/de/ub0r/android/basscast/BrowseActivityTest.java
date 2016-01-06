package de.ub0r.android.basscast;

import org.mockito.Mockito;

import android.test.ActivityInstrumentationTestCase2;

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

}
