package de.ub0r.android.basscast;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.View;

public class BelowToolbarBehavior extends CoordinatorLayout.Behavior {
    public BelowToolbarBehavior() {
        super();
    }

    public BelowToolbarBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(final CoordinatorLayout parent, final View child, final View dependency) {
        return dependency.getId() == R.id.toolbar;
    }

    @Override
    public boolean onDependentViewChanged(final CoordinatorLayout parent, final View child, final View dependency) {
        updateChild(child, dependency);
        return true;
    }

    @Override
    public boolean onLayoutChild(final CoordinatorLayout parent, final View child, final int layoutDirection) {
        parent.onLayoutChild(child, layoutDirection);
        updateChild(child, parent.findViewById(R.id.toolbar));
        return true;
    }

    private void updateChild(final View child, final View toolbar) {
        child.setTranslationY(toolbar.getY() + toolbar.getLayoutParams().height);
    }
}
