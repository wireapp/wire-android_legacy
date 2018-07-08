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
package com.waz.zclient.pages.main.conversationlist.views.listview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import com.waz.zclient.R;
import com.waz.zclient.ui.pullforaction.OverScrollListener;
import com.waz.zclient.ui.pullforaction.OverScrollMode;
import com.waz.zclient.ui.pullforaction.PullForActionView;
import timber.log.Timber;


/**
 * ListView subclass that provides the swipe functionality
 */
public class SwipeListView extends RecyclerView implements PullForActionView {
    public static final String TAG = SwipeListView.class.getName();

    // touch states to distinguish between list and drawer
    private final static int TOUCH_STATE_REST = 0;
    private final static int TOUCH_STATE_SCROLLING_X = 1;
    private final static int TOUCH_STATE_SCROLLING_Y = 2;
    private int touchState = TOUCH_STATE_REST;

    // used to track touch movement
    private float lastMotionX;
    private float lastMotionY;

    private int touchSlop;

    // Cached ViewConfiguration and system-wide constant values
    private int minFlingVelocity;
    private int maxFlingVelocity;

    // drawer goes to....
    private float rightOffset = 0;

    // the listener that wants to be informed by the overscroll event
    OverScrollListener overScrollListener;

    // no overscroll needed from here, instead we dispatch overscrolling to the container
    private int maxOverScrollDistance = 0;

    // initialize rect once to help performance. Used to determine of touch is on view
    private Rect targetRect = new Rect();

    // Fixed properties
    private int viewWidth = 1; // 1 and not 0 to prevent dividing by zero

    // the initial touch down position
    private float downX;

    // tracks touch move to calculate swipe speed
    private VelocityTracker velocityTracker;

    // the touched view from the list
    private SwipeListRow targetView;

    // tracking the child position to see if updates are necessary
    private int targetChildPosition;

    // Max distance needed to swipe to fully reveal the menu indicator
    private int listRowMenuIndicatorMaxSwipeOffset;

    // flags target open
    boolean isItemOpen;

    boolean allowSwipeAway;

    /**
     * Opens the drawer by calling an animation on an item.
     */
    private void openItem() {
        isItemOpen = true;
        if (targetView != null) {
            targetView.open();
        }
    }

    /**
     * Closes the drawer by calling an animation on an item.
     */
    private void closeItem() {
        isItemOpen = false;
        if (targetView != null) {
            targetView.close();
        }
    }

    /**
     * Sets the overscroll listener from the PullToRefreshContainer.
     */
    public void setOverScrollListener(OverScrollListener listener) {
        overScrollListener = listener;
    }


    public SwipeListView(Context context, @Nullable AttributeSet attrs) {
        super(new ContextWrapperEdgeEffect(context), attrs, 0);
        init();
    }

    public SwipeListView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(new ContextWrapperEdgeEffect(context), attrs, defStyle);
        init();
    }

    public SwipeListView(Context context) {
        super(new ContextWrapperEdgeEffect(context));
        init();
    }

    /**
     * Checking animation const and wrapping context to get rod of overscroll animation.
     */
    private void init() {
        ((ContextWrapperEdgeEffect) getContext()).setEdgeEffectColor(Color.TRANSPARENT);

        touchSlop = 10; //ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        listRowMenuIndicatorMaxSwipeOffset = getContext().getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__max_swipe_offset);
    }

    /**
     * Set offset on right after onMeasurement is called in PullToRefreshContainer.
     */
    public void setOffsetRight(float offsetRight) {
        rightOffset = offsetRight;
    }

    /**
     * onInterceptTouchEvent is not called on move even it returns false. (Bug?)
     * This function determines the hit element and the movement.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (viewWidth < 2) {
            viewWidth = getWidth();
        }
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_DOWN:
                touchState = TOUCH_STATE_REST;
                getHitChild(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                if (touchState != TOUCH_STATE_REST) {
                    super.dispatchTouchEvent(ev);
                }
                final float x = ev.getX();
                final float y = ev.getY();

                // make sure we hit an item
                if (targetView != null) {
                    checkMotionDirection(x, y);
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * @see android.widget.ListView#onInterceptTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchState = TOUCH_STATE_REST;
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                lastMotionX = x;
                lastMotionY = y;
                if (isItemOpen) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    //return true;
                }
                return super.onInterceptTouchEvent(ev);
            case MotionEvent.ACTION_MOVE:
                if (touchState != TOUCH_STATE_REST) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                velocityTracker.recycle();
                velocityTracker = null;
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    @SuppressWarnings("PMD.UselessOverridingMethod")
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    /**
     * @see android.widget.ListView#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // swiping is not enabled, let parent (ListView) eat that event
        if (targetView == null || !targetView.isSwipeable() || touchState == TOUCH_STATE_SCROLLING_Y) {
            return super.onTouchEvent(ev);
        }
        float deltaX = ev.getRawX() - downX;
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_MOVE:
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                targetView.setOffset(deltaX);
                return true;
            case MotionEvent.ACTION_UP:
                if (velocityTracker == null) {
                    break;
                }

                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);

                float velocityX = Math.abs(velocityTracker.getXVelocity());

                if (velocityTracker.getXVelocity() < 0) {
                    velocityX = 0;
                }

                float velocityY = Math.abs(velocityTracker.getYVelocity());

                if (targetView != null) {

                    boolean flingRight = false;

                    if (minFlingVelocity <= velocityX &&
                        velocityX <= maxFlingVelocity &&
                        velocityY * 2 < velocityX &&
                        deltaX > 0) {
                        flingRight = true;
                    }
                    if (allowSwipeAway && deltaX > viewWidth / 2) {
                        if (targetView != null) {
                            targetView.swipeAway();
                        }
                    } else if ((!allowSwipeAway && (deltaX > viewWidth / 2 || flingRight)) ||
                             (allowSwipeAway && (deltaX > viewWidth / 4 || flingRight))) {
                        if (targetView.isOpen()) {
                            closeItem();
                        } else {
                            openItem();
                        }
                    } else {
                        closeItem();
                    }
                }

                velocityTracker.recycle();
                velocityTracker = null;
                downX = 0;
                return true;
        }

        return super.onTouchEvent(ev);
    }

    /**
     * Check if the user is moving the cell or the list.
     */
    private void checkMotionDirection(float x, float y) {
        final int distX = (int) (x - lastMotionX);
        final int distY = (int) (y - lastMotionY);
        final boolean yMoved = Math.abs(distY) > this.touchSlop;

        // vertical is bigger than horizontal
        if (yMoved && Math.abs(distY) > Math.abs(distX)) {
            touchState = TOUCH_STATE_SCROLLING_Y;
            closeItem();
            return;
        }

        final boolean isMovingRight = distX > 0;
        final boolean xMoved = Math.abs(distX) > this.touchSlop;
        if (xMoved) {
            if (isMovingRight) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            touchState = TOUCH_STATE_SCROLLING_X;
            lastMotionX = x;
            lastMotionY = y;
        }
    }

    private MotionDirection getMotionDirection(float x, float y) {
        MotionDirection direction = null;

        final int distX = (int) (x - lastMotionX);
        final int distY = (int) (y - lastMotionY);
        boolean xMoved = Math.abs(distX) > this.touchSlop;
        boolean yMoved = Math.abs(distY) > this.touchSlop && Math.abs(distY) > Math.abs(distX);

        if (yMoved) {
            if (distY < 0) {
                direction = MotionDirection.UP;
            } else {
                direction = MotionDirection.DOWN;
            }
        } else if (xMoved) {
            if (distX < 0) {
                direction = MotionDirection.LEFT;
            } else {
                direction = MotionDirection.RIGHT;
            }
        }

        return direction;
    }

    /**
     * Retrieves the child of the list view that is hit by the touch event. If it
     * is the first item or a disabled one, targetView is set to null.
     */
    private void getHitChild(MotionEvent motionEvent) {
        int[] listViewCoords = new int[2];
        getLocationOnScreen(listViewCoords);
        int x = (int) motionEvent.getRawX() - listViewCoords[0];
        int y = (int) motionEvent.getRawY() - listViewCoords[1];
        int childCount = getChildCount();
        View child;
        for (int i = 0; i < childCount; i++) {
            child = getChildAt(i);
            child.getHitRect(targetRect);
            if (targetRect.contains(x, y)) {
                int position = getChildLayoutPosition(child);
                boolean allowSwipe = child instanceof SwipeListRow && ((SwipeListRow) child).isSwipeable();
                if (allowSwipe) {
                    if (position != targetChildPosition) {
                        closeItem();
                    }
                    targetChildPosition = position;

                    // TODO: Investigate causes of ClassCastExecption. Perhaps archiving related views?
                    try {
                        targetView = (SwipeListRow) child;
                        targetView.setMaxOffset(allowSwipeAway ? viewWidth / 2 : listRowMenuIndicatorMaxSwipeOffset);
                    } catch (ClassCastException e) {
                        Timber.e(e, "ClassCastException when swiping");
                    }
                    downX = motionEvent.getRawX();
                } else {
                    closeItem();
                    targetView = null;
                }
                return;
            }
        }

        // no child hit
        closeItem();
        targetView = null;
    }

    /**
     * The BouncingListVieContainer is notified from this spot.
     * At this place the default MaxOverscrollY can be overwritten.
     */
    @Override
    protected boolean overScrollBy(int deltaX,
                                   int deltaY,
                                   int scrollX,
                                   int scrollY,
                                   int scrollRangeX,
                                   int scrollRangeY,
                                   int maxOverScrollX,
                                   int maxOverScrollY,
                                   boolean isTouchEvent) {

        if (overScrollListener != null) {
            if (deltaY < 0) {
                overScrollListener.onOverScrolled(OverScrollMode.TOP);
            } else {
                overScrollListener.onOverScrolled(OverScrollMode.BOTTOM);
            }
        }
        return super.overScrollBy(deltaX,
                                  deltaY,
                                  scrollX,
                                  scrollY,
                                  scrollRangeX,
                                  scrollRangeY,
                                  maxOverScrollX,
                                  maxOverScrollDistance,
                                  isTouchEvent);
    }

    public void onPagerOffsetChanged(float offset) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view instanceof SwipeListRow) {
                ((SwipeListRow) view).setPagerOffset(offset);
            }
        }
    }

    public void resetListRowALpha() {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view instanceof SwipeListRow) {
                ((SwipeListRow) view).dimOnListRowMenuSwiped(1f);
            }
        }
    }

    public void setAllowSwipeAway(boolean allowSwipeAway) {
        this.allowSwipeAway = allowSwipeAway;
    }

    public interface SwipeListRow {
        void open();
        void close();
        void setMaxOffset(float maxOffset);
        void setOffset(float offset);
        boolean isSwipeable();
        boolean isOpen();
        void swipeAway();
        void dimOnListRowMenuSwiped(float alpha);
        void setPagerOffset(float pagerOffset);
    }

}
