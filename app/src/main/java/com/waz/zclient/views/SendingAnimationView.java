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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.waz.zclient.R;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;

public class SendingAnimationView extends View implements ValueAnimator.AnimatorUpdateListener {
    private static final int DEFAULT_RADIUS = -1;
    private static final int DEFAULT_STROKE_WIDTH_DP = 1;
    private static final int DEFAULT_ANIMATION_DURATION = 1500;
    private static final float CIRCLE_FILL_ANIMATION_PART = 0.8f;
    private static final int DEFAULT_PENDING_ANGLE_STOP = 270;

    private Paint circlePaint;
    private Paint fullCirclePaint;
    private Paint backgroundCirclePaint;
    private int strokeWidth;
    private RectF rectF;

    private float angle;
    private int animationDuration;
    private int radius;
    private int inputRadius;
    private ValueAnimator circleStartAnimator;
    private ValueAnimator circleEndAnimator;
    private ValueAnimator scalingAnimator;
    private ValueAnimator nextAnimation;

    private int backgroundCircleRadius = DEFAULT_RADIUS;
    private int backgroundCirclePadding = 0;
    private float scale;
    private int pendingAngleStop;

    public SendingAnimationView(Context context) {
        super(context);
        init(null);
    }

    public SendingAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SendingAnimationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        resetValues();

        radius = DEFAULT_RADIUS;
        animationDuration = DEFAULT_ANIMATION_DURATION;
        pendingAngleStop = DEFAULT_PENDING_ANGLE_STOP;
        int backgroundColor = ColorUtils.injectAlpha(204, Color.WHITE);
        int backgroundCircleColor = Color.TRANSPARENT;
        int circleColor = Color.WHITE;

        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.SendingAnimationView);
            if (ta != null) {
                circleColor = ta.getColor(R.styleable.SendingAnimationView_progressColor, circleColor);
                backgroundColor = ta.getColor(R.styleable.SendingAnimationView_fullCircleColor, backgroundColor);
                backgroundCircleRadius = ta.getDimensionPixelSize(R.styleable.SendingAnimationView_backgroundCircleRadius, DEFAULT_ANIMATION_DURATION);
                backgroundCircleColor = ta.getColor(R.styleable.SendingAnimationView_backgroundColor, backgroundCircleColor);
                backgroundCirclePadding = ta.getDimensionPixelSize(R.styleable.SendingAnimationView_circlePadding, 0);
                inputRadius = ta.getDimensionPixelSize(R.styleable.SendingAnimationView_circleRadius, radius);
                pendingAngleStop = ta.getInt(R.styleable.SendingAnimationView_pendingStop, pendingAngleStop);
                animationDuration = ta.getInt(R.styleable.SendingAnimationView_pendingDuration, animationDuration);
                ta.recycle();
            }
        }

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(circleColor);
        circlePaint.setStrokeWidth(strokeWidth);

        fullCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fullCirclePaint.setStyle(Paint.Style.STROKE);
        fullCirclePaint.setColor(backgroundColor);
        fullCirclePaint.setStrokeWidth(strokeWidth);

        backgroundCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundCirclePaint.setStyle(Paint.Style.FILL);
        backgroundCirclePaint.setColor(backgroundCircleColor);

        recalculateRect();
    }

    private void resetValues() {
        nextAnimation = null;
        angle = 0f;
        scale = 1f;
        strokeWidth = ViewUtils.toPx(getContext(), DEFAULT_STROKE_WIDTH_DP);
    }

    private void initAnimations() {
        circleStartAnimator = ObjectAnimator.ofFloat(0, pendingAngleStop);
        circleStartAnimator.addUpdateListener(this);
        circleStartAnimator.setDuration((long) (animationDuration * (CIRCLE_FILL_ANIMATION_PART / 4f * 3)));
        circleStartAnimator.setInterpolator(new LinearInterpolator());
        circleStartAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                SendingAnimationView.super.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (nextAnimation != null) {
                    nextAnimation.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        circleEndAnimator = ObjectAnimator.ofFloat(DEFAULT_PENDING_ANGLE_STOP, 360);
        circleEndAnimator.addUpdateListener(this);
        circleEndAnimator.setDuration((long) (animationDuration * (CIRCLE_FILL_ANIMATION_PART / 4f)));
        circleEndAnimator.setInterpolator(new LinearInterpolator());
        circleEndAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                SendingAnimationView.super.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                scalingAnimator.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        scalingAnimator = ObjectAnimator.ofFloat(1, 0);
        scalingAnimator.addUpdateListener(this);
        scalingAnimator.setDuration((long) (animationDuration * (1f - CIRCLE_FILL_ANIMATION_PART)));
        scalingAnimator.setInterpolator(new LinearInterpolator());
        scalingAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}

            @Override
            public void onAnimationEnd(Animator animation) {
                resetValues();
                SendingAnimationView.super.setVisibility(GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                resetValues();
                SendingAnimationView.super.setVisibility(GONE);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        recalculateRect();
    }

    private void recalculateRect() {
        if (rectF == null) {
            rectF = new RectF();
        }
        rectF.set(strokeWidth + getPaddingLeft() + backgroundCirclePadding,
                  strokeWidth + getPaddingTop() + backgroundCirclePadding,
                  getWidth() - getPaddingLeft() - getPaddingRight() - strokeWidth - backgroundCirclePadding,
                  getHeight() - getPaddingBottom() - getPaddingTop() - strokeWidth - backgroundCirclePadding);

        recalculateRadius();
    }

    private void recalculateRadius() {
        if (inputRadius != DEFAULT_RADIUS) {
            radius = inputRadius - strokeWidth;
            float centerX = rectF.centerX();
            float centerY = rectF.centerY();
            rectF.set(centerX - radius,
                      centerY - radius,
                      centerX + radius,
                      centerY + radius);
            return;
        }
        float width = (rectF.right - rectF.left);
        float height = (rectF.bottom - rectF.top);

        radius = (int) (Math.min(width, height) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (MathUtils.floatEqual(rectF.centerX(), 0f) ||
            MathUtils.floatEqual(rectF.centerY(), 0f)) {
            recalculateRect();
        }

        float centerX = rectF.centerX();
        float centerY = rectF.centerY();

        canvas.drawCircle(centerX,
                          centerY,
                          backgroundCircleRadius == DEFAULT_RADIUS ? radius + strokeWidth + backgroundCirclePadding
                                                                   : backgroundCircleRadius,
                          backgroundCirclePaint);
        canvas.drawCircle(centerX, centerY, scale * radius, fullCirclePaint);
        if (circlePaint.getStyle() == Paint.Style.STROKE) {
            canvas.drawArc(rectF, 0, angle, false, circlePaint);
        } else {
            canvas.drawCircle(centerX, centerY, scale * radius, circlePaint);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        setAnimate(visibility == VISIBLE);
    }

    @SuppressLint("NewApi")
    private void setAnimate(boolean animate) {
        if (animate) {
            if (circleStartAnimator == null) {
                initAnimations();
            }
            if (!circleStartAnimator.isRunning() && MathUtils.floatEqual(angle, 0f)) {
                circleStartAnimator.start();
            }
        } else {
            if (circleStartAnimator == null) {
                super.setVisibility(GONE);
            } else if (MathUtils.floatEqual(angle, DEFAULT_PENDING_ANGLE_STOP)) {
                circleEndAnimator.start();
            } else if (circleStartAnimator.isRunning()) {
                nextAnimation = circleEndAnimator;
            }
        }
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        if (animation.equals(circleStartAnimator) || animation.equals(circleEndAnimator)) {
            angle = (float) animation.getAnimatedValue();
        } else {
            scale = (float) animation.getAnimatedValue();
            if (circlePaint.getStyle() == Paint.Style.STROKE) {
                circlePaint.setStyle(Paint.Style.FILL);
            }
        }
        invalidate();
    }
}
