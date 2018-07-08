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
package com.waz.zclient.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;

public class InfiniteLoadingBarView extends View {
    private static final int DEFAULT_LOADING_BAR_COLOR = Color.RED;

    private static final Property<InfiniteLoadingBarView, Float> TIME = new Property<InfiniteLoadingBarView, Float>(Float.class, "time") {
        @Override
        public Float get(InfiniteLoadingBarView object) {
            return object.getTime();
        }

        @Override
        public void set(InfiniteLoadingBarView object, Float value) {
            object.setTime(value);
        }
    };
    private Paint loadingBarPaint;
    private int strokeWidth;
    private int strokeGap;
    private int animationDuration;

    private ObjectAnimator timeAnimator;
    private float time;

    public InfiniteLoadingBarView(Context context) {
        super(context);
        init();
    }

    public InfiniteLoadingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InfiniteLoadingBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeGap = getResources().getDimensionPixelSize(R.dimen.loading_bar__gap_size);
        strokeWidth = getResources().getDimensionPixelSize(R.dimen.loading_bar__stroke_width);

        loadingBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadingBarPaint.setColor(DEFAULT_LOADING_BAR_COLOR);
        loadingBarPaint.setStyle(Paint.Style.STROKE);
        loadingBarPaint.setStrokeWidth(strokeWidth);

        animationDuration = getResources().getInteger(R.integer.loading_bar__animation_duration);

        setAlpha(0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        startAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null) {
            return;
        }
        canvas.drawLine(-strokeGap,
                        strokeWidth / 2,
                        time * (canvas.getWidth() + strokeGap) - strokeGap,
                        strokeWidth / 2,
                        loadingBarPaint);
        canvas.drawLine(time * (canvas.getWidth() + strokeGap),
                        strokeWidth / 2,
                        canvas.getWidth(),
                        strokeWidth / 2,
                        loadingBarPaint);
    }

    private void startAnimation() {
        stopAnimation();
        timeAnimator = ObjectAnimator.ofFloat(this, TIME, 0f, 1f);
        timeAnimator.setRepeatCount(ValueAnimator.INFINITE);
        timeAnimator.setDuration(animationDuration);
        timeAnimator.setRepeatMode(ValueAnimator.RESTART);
        timeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                invalidate();
            }
        });
        timeAnimator.setInterpolator(new Quart.EaseInOut());
        timeAnimator.start();

    }

    private void stopAnimation() {
        if (timeAnimator == null) {
            return;
        }
        timeAnimator.setRepeatCount(1);
        timeAnimator.cancel();
        timeAnimator = null;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public float getTime() {
        return time;
    }

    public void setColor(int color) {
        loadingBarPaint.setColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animate().alpha(1);
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().alpha(0);
        stopAnimation();
    }
}
