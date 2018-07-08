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
package com.waz.zclient.views.calling;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.ui.animation.interpolators.penner.Back;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.views.properties.PositionAnimatable;

public class CallingGainView extends View implements PositionAnimatable {
    public static final String TAG = CallingGainView.class.getName();
    private static final int DEFAULT_DURATION = 700;
    private static final int DEFAULT_ALPHA_DELAY = 100;
    private static final int DEFAULT_COLOR = Color.BLACK;
    private static final float MIN_ALPHA = 0.0f;
    private static final float MAX_ALPHA = 0.5f;

    private float gain;
    private int duration;

    private Paint paint;

    private ObjectAnimator scale;
    private float animationPosition;

    private int outerRadius;

    private int centerX;
    private int centerY;

    public CallingGainView(Context context) {
        super(context);
        init();
    }

    public CallingGainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CallingGainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    public void setGainColor(int color) {
        paint.setColor(color);
    }

    @Override
    public void setAnimationPosition(float animationPosition) {
        this.animationPosition = animationPosition;
        invalidate();
    }

    @Override
    public float getAnimationPosition() {
        return animationPosition;
    }

    private void init() {
        duration = DEFAULT_DURATION;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(DEFAULT_COLOR);
        paint.setStyle(Paint.Style.FILL);
        setAlpha(0);
        scale = ObjectAnimator.ofFloat(this, ANIMATION_POSITION, 0, 1);
        scale.setDuration(duration);
    }


    /**
     * Changing the radius of the glow.
     *
     * @param volume Should be a value between 0.0f - 1.0f.
     *               However, currently AVS is providing linear values, so we currently
     *               apply a logarithmic function to the incoming values to get the true gain.
     */
    public void onGainHasChanged(float volume) {

        //Apply logarithmic scale to linear volume values, clamping between (0, 1)
        float gain = (float) Math.min(Math.max(0.5 * Math.log10((double) volume * 2.5) + 1, 0), 1);

        scale.cancel();
        this.gain = gain;
        setAlpha(MAX_ALPHA);
        scale.setInterpolator(new Back.EaseOut(2 * gain + 1));
        scale.start();
        animate().alpha(MIN_ALPHA)
                 .setDuration(duration)
                 .setStartDelay(DEFAULT_ALPHA_DELAY)
                 .setInterpolator(new Quart.EaseOut())
                 .start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int actualWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int actualHeight = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom() - getPaddingTop();


        centerX = getPaddingLeft() + actualWidth / 2;
        centerY = getPaddingTop() + actualHeight / 2;
        outerRadius = 2 * actualWidth / 5;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int radius = (int) (animationPosition * (0.8f + 0.2f *  gain) * outerRadius);
        // Bump up the radius a bit, but make sure circle stays within bounds of the view
        canvas.drawCircle(centerX, centerY, Math.min(radius * 1.2f, getWidth() / 2), paint);
    }
}
