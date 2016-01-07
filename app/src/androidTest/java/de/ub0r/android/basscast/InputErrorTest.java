package de.ub0r.android.basscast;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;

/**
 * @author flx
 */
@SuppressLint("SetTextI18n")
public class InputErrorTest extends ActivityInstrumentationTestCase2<EditStreamActivity> {

    public InputErrorTest() {
        super(EditStreamActivity.class);
    }

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public void testShowsErrorOnView() {
        setActivityIntent(new Intent(Intent.ACTION_INSERT));
        final EditStreamActivity activity = getActivity();

        final InputError error = new InputError("Invalid content", R.string.app_name);
        assertNull(activity.mTitleView.getError());

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                error.show(activity, activity.mTitleView);
            }
        });
        assertNotNull(activity.mTitleView.getError());
        assertEquals(activity.getString(R.string.app_name),
                activity.mTitleView.getError().toString());
    }
}
