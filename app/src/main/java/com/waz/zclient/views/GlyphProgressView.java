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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.CallSuper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;

public class GlyphProgressView extends GlyphTextView {

    private static final int DEFAULT_START_ANGLE = 270;
    private static final float ENDLESS_PROGRESS_VALUE = 0.85F;
    private static final int DEFAULT_STROKE_WIDTH_DP = 2;

    private float progress;
    private Paint progressPaint;
    private RectF rectF;
    private int strokeWidth;
    private int startAngle;
    private ValueAnimator endlessValueAnimator;

    public GlyphProgressView(Context context) {
        this(context, null);
    }

    public GlyphProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlyphProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @CallSuper
    protected void init() {
        strokeWidth = ViewUtils.toPx(getContext(), DEFAULT_STROKE_WIDTH_DP);
        progressPaint = new Paint();
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.STROKE);

        updateRectF();
        startAngle = DEFAULT_START_ANGLE;
        setGravity(Gravity.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getSize() <= 1 || MathUtils.floatEqual(progress, 0)) {
            return;
        }
        // progress bar
        canvas.drawArc(rectF, startAngle, (progress * 360), false, progressPaint);
    }

    public void startEndlessProgress() {
        if (endlessValueAnimator != null && endlessValueAnimator.isRunning()) {
            return;
        }
        endlessValueAnimator = ValueAnimator.ofInt(0, 360);
        endlessValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                startAngle = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        progress = ENDLESS_PROGRESS_VALUE;
        endlessValueAnimator.setDuration(1500);
        endlessValueAnimator.setInterpolator(new LinearInterpolator());
        endlessValueAnimator.setRepeatCount(ValueAnimator.INFINITE);
        endlessValueAnimator.start();
    }

    public void setProgress(float progress) {
        if (endlessValueAnimator != null) {
            if (endlessValueAnimator.isRunning()) {
                endlessValueAnimator.cancel();
            }
            endlessValueAnimator = null;
            startAngle = DEFAULT_START_ANGLE;
        }
        this.progress = progress;
        invalidate();
    }

    public void clearProgress() {
        if (endlessValueAnimator != null && endlessValueAnimator.isRunning()) {
            endlessValueAnimator.end();
        }
        endlessValueAnimator = null;
        startAngle = DEFAULT_START_ANGLE;
        progress = 0;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public boolean isAnimatingEndlessProgress() {
        return endlessValueAnimator != null && endlessValueAnimator.isRunning();
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
    }

    private float getSize() {
        return Math.min(getWidth(), getHeight());
    }

    private void updateRectF() {
        if (rectF == null) {
            rectF = new RectF();
        }
        float left = (getWidth() - getSize()) / 2 + (strokeWidth / 2);
        float top = (getHeight() - getSize()) / 2 + (strokeWidth / 2);
        float right = left + getSize() - strokeWidth;
        float bottom = top + getSize() - strokeWidth;
        rectF.set(left, top, right, bottom);
    }
}
