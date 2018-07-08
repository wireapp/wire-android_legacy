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
package com.waz.zclient.pages.main.popup;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class ViewPagerLikeRecyclerView extends RecyclerView implements GestureDetector.OnGestureListener {

    private GestureDetectorCompat gestureDetector;
    private int center;

    public ViewPagerLikeRecyclerView(Context context) {
        this(context, null);
    }

    public ViewPagerLikeRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewPagerLikeRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gestureDetector = new GestureDetectorCompat(context, this);
    }

    @Override
    public void setLayoutManager(LayoutManager layoutManager) {
        super.setLayoutManager(layoutManager);
        if (!(layoutManager instanceof ViewPagerLikeLayoutManager)) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        final ViewPagerLikeLayoutManager lm = (ViewPagerLikeLayoutManager) getLayoutManager();
        super.smoothScrollToPosition(lm.getPositionForVelocity(velocityX));
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        gestureDetector.onTouchEvent(e);
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP && e.getAction() != MotionEvent.ACTION_CANCEL) {
            return super.onTouchEvent(e);
        }

        if (getScrollState() != SCROLL_STATE_IDLE) {
            return super.onTouchEvent(e);
        }

        smoothScrollToPosition(((ViewPagerLikeLayoutManager) getLayoutManager()).getFixScrollPos());
        return super.onTouchEvent(e);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        center = MeasureSpec.getSize(widthSpec) / 2;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (getAdapter().getItemCount() == 0) {
            return false;
        }
        final ViewPagerLikeLayoutManager lm = (ViewPagerLikeLayoutManager) getLayoutManager();
        final int currentPosition = lm.findFirstVisibleItemPosition();
        final int targetPosition;
        if (e.getX() - center < 0) {
            targetPosition = Math.max(currentPosition - 1, 0);
        } else {
            targetPosition = Math.min(currentPosition + 1, getAdapter().getItemCount() - 1);
        }
        super.smoothScrollToPosition(targetPosition);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }
}
