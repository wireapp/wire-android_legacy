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
package com.waz.zclient.pages.main.conversation.views.image;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.ui.animation.interpolators.penner.Cubic;

public class ImageDragViewContainer extends FrameLayout {

    private static final float MAX_DISTANCE_SCALE_FACTOR = 1.5f;
    private static final int MAX_ROTATION = 24;
    private static final int MIN_ROTATION = 5;
    private static final float FLING_DURATION_SECONDS = 0.05f;
    private static final Interpolator DISTANCE_INTERPOLATOR = new Cubic.EaseIn();
    private static final float NORMALIZED_FLING_DISTANCE_THRESHOLD = 0.3f;

    private ImageDragViewHelper dragHelper;
    private boolean enabled = true;
    private ImageViewDragCallback imageViewDragCallback;
    private Callback callback;

    public ImageDragViewContainer(Context context) {
        this(context, null);
    }

    public ImageDragViewContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageDragViewContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        imageViewDragCallback = new ImageViewDragCallback();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dragHelper = ImageDragViewHelper.create(this, 1.0f, imageViewDragCallback);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev == null) {
            return false;
        }

        if (ev.getPointerCount() != 1) {
            return false;
        }

        boolean shouldDrag;
        try {
            shouldDrag = dragHelper.shouldInterceptTouchEvent(ev);
        } catch (Exception e) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            imageViewDragCallback.startX = ev.getX();
            imageViewDragCallback.startY = ev.getY();
            imageViewDragCallback.centerX = getLeft() + (getRight() - getLeft()) / 2f;
            imageViewDragCallback.centerY = getTop() + (getBottom() - getTop()) / 2f;
        }

        boolean intercept = enabled && shouldDrag;
        if (callback != null && intercept) {
            callback.onStartDrag();
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        try {
            dragHelper.processTouchEvent(ev);
        } catch (Exception ignored) { }
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else if (imageViewDragCallback.isFlinging) {
            imageViewDragCallback.settled();
        }
    }

    public void setDraggingEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onFlingRequested(float left, float top, float rotation, float pivotX, float pivotY);

        void onDragDistance(float distance);

        void onStartDrag();

        void onEndDrag();
    }

    private class ImageViewDragCallback extends ImageDragViewHelper.Callback {

        private float lastRotation;
        public float centerX = 0;
        public float centerY = 0;
        public float startX = 0;
        public float startY = 0;
        public int x = 0;
        public int y = 0;
        private boolean isFlinging;

        @Override
        public boolean tryCaptureView(View view, int i) {
            return true;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return child.getMeasuredHeight();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            super.onViewReleased(releasedChild, xvel, yvel);
            float normalizedDistance = getNormalizedDragDistance();
            if (normalizedDistance > NORMALIZED_FLING_DISTANCE_THRESHOLD && callback != null) {
                isFlinging = true;
                dragHelper.settleCapturedViewAt(releasedChild.getLeft(),
                                                (int) (releasedChild.getTop() + (yvel / 1.25f) * FLING_DURATION_SECONDS));
            } else {
                if (callback != null) {
                    callback.onEndDrag();
                }
                dragHelper.settleCapturedViewAt(0, 0);
            }
            postInvalidate();
        }

        private float getNormalizedDragDistance() {
            return (float) MathUtils.clamp(Math.abs(y / (MAX_DISTANCE_SCALE_FACTOR * centerY)), 0, 1);
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            x += dx;
            y -= dy;
            float normalizedDistance = getNormalizedDragDistance();
            if (callback != null) {
                callback.onDragDistance(normalizedDistance);
            }
            changedView.setPivotX(startX);
            changedView.setPivotY(startY);
            float maxRotation = (float) (MAX_ROTATION * MathUtils.clamp(Math.abs(startX - centerX),
                                                                         MIN_ROTATION / MAX_ROTATION,
                                                                         1f));
            changedView.setRotation(maxRotation * DISTANCE_INTERPOLATOR.getInterpolation(normalizedDistance) *
                                    Math.signum(-y) * Math.signum(startX - centerX));
            lastRotation = changedView.getRotation();
        }

        public void settled() {
            isFlinging = false;
            if (callback != null) {
                callback.onFlingRequested(x,
                                          -y,
                                          lastRotation,
                                          startX,
                                          startY);
            }
        }
    }
}
