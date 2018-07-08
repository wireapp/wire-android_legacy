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
package com.waz.zclient.pages.main.profile.camera;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.R;
import com.waz.zclient.ui.views.properties.PositionAnimatable;

public class CameraFocusView extends View implements PositionAnimatable {
    private Paint drawingPaint;

    private float animationPosition = 1;
    private int maxSize;
    private int minSize;

    public CameraFocusView(Context context) {
        this(context, null);
    }

    public CameraFocusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraFocusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        drawingPaint = new Paint();
        drawingPaint.setColor(Color.BLUE);
        drawingPaint.setStyle(Paint.Style.STROKE);
        drawingPaint.setStrokeWidth(getResources().getDimension(R.dimen.camera__focus__stroke_width));
        maxSize = getResources().getDimensionPixelSize(R.dimen.camera__focus__max_size);
        minSize = getResources().getDimensionPixelSize(R.dimen.camera__focus__min_size);
        setAlpha(0);
    }

    public void hideFocusView() {
        setAlpha(0);
    }

    public void showFocusView() {
        setAlpha(1);
        ObjectAnimator.ofFloat(this, ANIMATION_POSITION, 0, 1).setDuration(getResources().getInteger(R.integer.camera__focus__animation_duration)).start();
    }

    public void setColor(int color) {
        drawingPaint.setColor(color);
    }

    public float getAnimationPosition() {
        return animationPosition;
    }

    public void setAnimationPosition(float animationPosition) {
        this.animationPosition = animationPosition;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int maxRadius = maxSize / 2;
        int x = getWidth() / 2;
        int y = getHeight() / 2;
        int radius = (int) (maxRadius - (maxSize - minSize) / 2 * animationPosition);
        canvas.drawCircle(x, y, radius, drawingPaint);
    }

}
