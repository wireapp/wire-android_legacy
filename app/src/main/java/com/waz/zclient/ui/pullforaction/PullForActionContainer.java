/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.ui.pullforaction;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.waz.zclient.ui.views.properties.OffsetAnimateable;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;


/**
 * The PullToRefreshContainer is a FrameLayout that eats up all touch events and in case
 * of an overscroll called by the BouncingListView it translates the ListView to the overscroll
 * position. If ACTION_UP happens or the Overscroll offset goes into another direction the TouchEvent
 * is dispatched to the BouncingListView.
 */
public class PullForActionContainer extends FrameLayout implements OverScrollListener,
                                                                   OffsetAnimateable {
    /**
     * class tag
     */
    private static final float ALPHA_BOTTOM_TRESHOLD = 0.35f;


    /**
     * Default values
     */
    private static final int DEFAULT_RELEASE_TRESHOLD_TOP = 100;
    private static final int DEFAULT_RELEASE_TRESHOLD_BOTTOM = 100;
    private static final int DEFAULT_ANIMATION_DURATION_TOP = 550;
    private static final int DEFAULT_ANIMATION_DURATION_BOTTOM = 300;
    private static final int DEFAULT_MAX_OFFSET_TOP = 500;
    private static final float DEFAULT_RESISTANCE_TOP = 0.5f;
    private static final int DEFAULT_HOLDDOWN_MIN_TIME = 250;

    /**
     * Overscrollable view
     */
    private PullForActionView pullForActionView;

    // flag that indicates that an overscroll is detected
    private OverScrollMode overScrollMode = OverScrollMode.NONE;

    // the last y is the reference point for translating the listview
    private float lastY;
    private boolean isDown;

    // callback to parent
    private PullForActionListener pullToActionListener;

    private boolean animateActionBackTop;
    private boolean animateActionBackBottom;

    private boolean userWantsToReleaseTop;
    private boolean userWantsToReleaseBottom;

    private int releaseTresholdTop;
    private boolean animateActionView;
    private float alphaBottomTreshold;

    private int releaseTresholdBottom;

    private int animationDurationTop;
    private int animationDurationBottom;

    private int maxOffsetTop;

    private float resistanceTop;

    private int currentOffset;

    public enum FillType {
        FILL,
        WRAP
    }

    public void setPullForActionView(@NonNull PullForActionView pullForActionView, FillType fillType) {
        this.pullForActionView = pullForActionView;
        this.pullForActionView.setOverScrollListener(this);

        // The height has to be set to wrap content to make the pull work
        // properly for lists with too few items. Adjust your adapter to add a
        // fake view that makes the list act like it is matched to parent
        int heightType = ViewGroup.LayoutParams.MATCH_PARENT;
        switch (fillType) {
            case WRAP:
                heightType = ViewGroup.LayoutParams.WRAP_CONTENT;
                break;
        }

        addView((View) this.pullForActionView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                heightType));
    }

    private PullForActionMode pullForActionMode = PullForActionMode.TOP_AND_BOTTOM;

    public void setPullForActionMode(PullForActionMode pullForActionMode) {
        this.pullForActionMode = pullForActionMode;
    }


    public PullForActionContainer(Context context) {
        super(context);
        init();
    }

    public PullForActionContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PullForActionContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * initializes default values
     */
    public void init() {
        animateActionView = true;

        animateActionBackTop = true;
        animateActionBackBottom = true;

        alphaBottomTreshold = ALPHA_BOTTOM_TRESHOLD;

        releaseTresholdTop = ViewUtils.toPx(getContext(), DEFAULT_RELEASE_TRESHOLD_TOP);
        releaseTresholdBottom = ViewUtils.toPx(getContext(), DEFAULT_RELEASE_TRESHOLD_BOTTOM);

        animationDurationTop = DEFAULT_ANIMATION_DURATION_TOP;
        animationDurationBottom = DEFAULT_ANIMATION_DURATION_BOTTOM;

        maxOffsetTop = ViewUtils.toPx(getContext(), DEFAULT_MAX_OFFSET_TOP);

        resistanceTop = DEFAULT_RESISTANCE_TOP;
    }

    @Override
    public void setOffset(int offset) {
        currentOffset = offset;

        float lambda = 1 - (1.0f * Math.abs(offset)) / maxOffsetTop;
        float alpha = (float) Math.pow(lambda, 4);
        if (alpha < alphaBottomTreshold) {
            alpha = alphaBottomTreshold;
        }

        userWantsToReleaseTop = offset > releaseTresholdTop;
        userWantsToReleaseBottom = offset < -releaseTresholdBottom;

        if (animateActionView) {
            pullForActionView.setAlpha(alpha);
            pullForActionView.setTranslationY(offset);
        }

        notifyOffsetHasChanged(offset);
        notifyAlphaHasChanged(alpha);
    }

    private void notifyAlphaHasChanged(float alpha) {
        if (pullToActionListener != null) {
            pullToActionListener.setAlpha(alpha);
        }
    }

    @Override
    public int getOffset() {
        return currentOffset;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    /**
     * This method eats all TouchEvents and during a gesture it will not be called again.
     * If the first event is already an overscroll event the LastY should already be available.
     *
     * @return always true
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        lastY = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            isDown = true;
        }

        return true;
    }

    private long startTime;

    /**
     * If the ListView is not overscrolling the TouchEvent will be dispatched
     * to the ListView. Otherwise an bouncing logic is implemented.
     *
     * @return always true
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startTime = System.currentTimeMillis();
        }

        switch (overScrollMode) {
            case NONE:
                // no overscroll is detected - dispatch event
                lastY = event.getY();
                pullForActionView.dispatchTouchEvent(event);
                break;
            case BOTTOM:
                // user finished gesture
                // - bounce back the listview or/and start a conversation
                // or show archived conversations
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (userWantsToReleaseBottom && swipeTimeThresholdPassed()) {
                        overScrollMode = OverScrollMode.NONE;
                        notifyReleaseBottom();
                        if (!animateActionBackBottom) {
                            break;
                        }
                    }

                    ObjectAnimator animator = ObjectAnimator.ofInt(this, OFFSET, 0);
                    animator.setDuration(animationDurationBottom);
                    animator.setInterpolator(new Expo.EaseOut());
                    animator.start();

                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    // User is moving touch
                    // if the offset is positive, it will be passed to the handler
                    // - otherwise the event is dispatched to the listview
                    int offset = (int) (event.getY() - lastY);

                    if (offset > 0) {
                        overScrollMode = OverScrollMode.NONE;
                        setOffset(0);
                    } else {
                        setOffset((int) (offset * resistanceTop));
                    }
                }
                break;
            case TOP:
                // user finished gesture
                // - bounce back the listview or/and start a conversation
                // or show archived conversations
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (userWantsToReleaseTop && swipeTimeThresholdPassed()) {
                        overScrollMode = OverScrollMode.NONE;
                        int offset = (int) (event.getY() - lastY);
                        notifyReleaseTop(offset);

                        if (!animateActionBackTop) {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    setOffset(0);
                                }
                            }, animationDurationTop);
                            break;
                        }
                    }

                    ObjectAnimator animator = ObjectAnimator.ofInt(this, OFFSET, 0);
                    animator.setDuration(animationDurationTop);
                    animator.setInterpolator(new Expo.EaseOut());
                    animator.start();

                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    // User is moving touch
                    // if the offset is positive, it will be passed to the handler
                    // - otherwise the event is dispatched to the listview
                    int offset = (int) (event.getY() - lastY);
                    if (offset < 0) {
                        overScrollMode = OverScrollMode.NONE;
                        setOffset(0);
                    } else {
                        setOffset((int) (offset * resistanceTop));
                    }
                }
                break;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            overScrollMode = OverScrollMode.NONE;
            isDown = false;

            // reset the offset if the app went into the background before
            // the gesture was completed
            if (getWindowVisibility() == GONE) {
                setOffset(0);
            }
        }
        return true;
    }

    private boolean swipeTimeThresholdPassed() {
        long gestureTime = System.currentTimeMillis() - startTime;
        return gestureTime > DEFAULT_HOLDDOWN_MIN_TIME;
    }

    /**
     * Callback of the BouncingListView
     */
    @Override
    public void onOverScrolled(OverScrollMode mode) {
        if (!isEnabled()) {
            return;
        }
        if (isDown) {
            switch (mode) {
                case NONE:
                    overScrollMode = mode;
                    break;
                case BOTTOM:
                    if (pullForActionMode.equals(PullForActionMode.BOTTOM) || pullForActionMode.equals(PullForActionMode.TOP_AND_BOTTOM)) {
                        overScrollMode = mode;
                    } else {
                        overScrollMode = OverScrollMode.NONE;
                    }
                    break;
                case TOP:
                    if (pullForActionMode.equals(PullForActionMode.TOP) || pullForActionMode.equals(PullForActionMode.TOP_AND_BOTTOM)) {
                        overScrollMode = mode;
                    } else {
                        overScrollMode = OverScrollMode.NONE;
                    }
                    break;
            }

        }
    }

    /**
     * Sets the listener
     *
     * @param pullToActionListener
     */
    public void setPullToActionListener(PullForActionListener pullToActionListener) {
        this.pullToActionListener = pullToActionListener;
    }

    /**
     * Notifies the parent that user pulled enough to start a conversation
     */
    private void notifyReleaseTop(int offset) {
        if (pullToActionListener != null) {
            pullToActionListener.onReleasedTop(offset);
        }
    }

    private void notifyReleaseBottom() {
        if (pullToActionListener != null) {
            pullToActionListener.onReleasedBottom();
        }
    }

    private void notifyOffsetHasChanged(int offset) {
        if (pullToActionListener != null) {
            pullToActionListener.onListViewOffsetChanged(offset);
        }
    }
}
