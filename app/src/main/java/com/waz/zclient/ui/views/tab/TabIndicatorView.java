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
package com.waz.zclient.ui.views.tab;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import com.waz.zclient.R;

public class TabIndicatorView extends View {

    public static final Property<View, Integer> ANIMATION_POSITION = new Property<View, Integer>(Integer.class,
                                                                                                 "animationPos") {
        @Override
        public Integer get(View object) {
            return ((TabIndicatorView) object).getAnimationPosition();
        }

        @Override
        public void set(View object, Integer value) {
            ((TabIndicatorView) object).setAnimationPosition(value);
        }
    };

    private int posX;
    private int markerWidth;
    private final Paint paint;
    private final Path path;
    private ObjectAnimator animator;
    private boolean showDivider;

    public TabIndicatorView(Context context) {
        this(context, null);
    }

    public TabIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        markerWidth = getResources().getDimensionPixelSize(R.dimen.wire__padding__regular);

        path = new Path();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.wire__divider__height__thin));

        showDivider = true;
    }

    public void setAnimationPosition(int pos) {
        posX = pos;
        updatePath();
        invalidate();
    }

    public int getAnimationPosition() {
        return posX;
    }

    public void setPosition(int posX, boolean animate) {
        if (animator != null) {
            animator.cancel();
        }

        if (animate) {
            animator = ObjectAnimator.ofInt(this, ANIMATION_POSITION, this.posX, posX);
            animator.start();
        } else {
            setAnimationPosition(posX);
        }
    }

    public void setShowDivider(boolean showDivider) {
        this.showDivider = showDivider;
        invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updatePath();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (showDivider) {
            canvas.drawPath(path, paint);
        }
    }

    private void updatePath() {
        int height = getMeasuredHeight() - getPaddingBottom();
        path.reset();
        path.moveTo(getPaddingLeft(), height);
        path.lineTo(posX - markerWidth / 2 + getPaddingLeft(), height);
        path.lineTo(posX + getPaddingLeft(), getPaddingTop());
        path.lineTo(posX + markerWidth / 2 + getPaddingLeft(), height);
        path.lineTo(getMeasuredWidth() - getPaddingRight(), height);
    }
}
