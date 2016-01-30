package de.ub0r.android.basscast;

import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class ControlsAwareScrollingViewBehavior extends AppBarLayout.ScrollingViewBehavior {

    public ControlsAwareScrollingViewBehavior() {
        super();
    }

    public ControlsAwareScrollingViewBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(final CoordinatorLayout parent, final View child, final View dependency) {
        return super.layoutDependsOn(parent, child, dependency) || dependency.getId() == R.id.controls;
    }


    @Override
    public boolean onDependentViewChanged(final CoordinatorLayout parent, final View child, final View dependency) {
        boolean result = false;
        if (dependency.getId() == R.id.controls) {
            final ViewGroup.LayoutParams params = child.getLayoutParams();
            params.height = (int) (child.getY() + dependency.getY());
            result = true;
        }
        return super.onDependentViewChanged(parent, child, dependency) || result;
    }
}
