/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversation.views.row.message.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.R;

public class ProgressDotsView extends View {

    private final static int ANIMATION_DURATION = 350 * 3; //350ms between dots

    private final Paint lightPaint = new Paint();
    private final Paint darkPaint = new Paint();

    private final int dotSpacing;
    private final int dotRadius;

    private boolean expired;

    //seems as though the animator never actually reaches the last value you set, so we go to 3 instead of just to 2
    private final ValueAnimator animator = ValueAnimator.ofInt(0, 1, 2, 3).setDuration(ANIMATION_DURATION);
    private int darkDotIndex = 0;

    private final Drawable defaultBackground;


    public ProgressDotsView(Context context) {
        this(context, null);
    }

    public ProgressDotsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressDotsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        lightPaint.setColor(context.getResources().getColor(R.color.graphite_16));
        darkPaint.setColor(context.getResources().getColor(R.color.graphite_40));
        dotSpacing = context.getResources().getDimensionPixelSize(R.dimen.progress_dot_spacing_and_width);
        dotRadius = dotSpacing / 2;

        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(null);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                darkDotIndex = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        defaultBackground = getBackground();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
        if (expired) {
            setBackgroundResource(R.drawable.shape_audio_message_background_expired);
        } else {
            setBackground(defaultBackground);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (expired) {
            return;
        }
        final int centerX = getWidth() / 2;
        final int centerY = getHeight() / 2;

        final int dotLeftCenterX = centerX - dotSpacing - dotRadius;
        final int dotRightCenterX = centerX + dotSpacing + dotRadius;

        canvas.drawCircle(dotLeftCenterX, centerY, dotRadius, darkDotIndex == 0 ? darkPaint : lightPaint);
        canvas.drawCircle(centerX, centerY, dotRadius, darkDotIndex == 1 ? darkPaint : lightPaint);
        canvas.drawCircle(dotRightCenterX, centerY, dotRadius, darkDotIndex == 2 ? darkPaint : lightPaint);
    }
}
