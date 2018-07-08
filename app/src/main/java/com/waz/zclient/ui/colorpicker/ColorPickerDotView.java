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
package com.waz.zclient.ui.colorpicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import com.waz.zclient.R;

public class ColorPickerDotView extends View implements ColorPickerView {

    final int smallDotRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_small_dot_radius);
    final int mediumDotRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_medium_dot_radius);
    final int largeDotRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_large_dot_radius);

    final int smallRingRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_small_ring_radius);
    final int mediumRingRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_medium_ring_radius);
    final int largeRingRadius = getResources().getDimensionPixelSize(R.dimen.color_picker_large_ring_radius);

    final int selectedRingSize = getResources().getDimensionPixelSize(R.dimen.color_picker_ring_size);

    private Paint circlePaint;
    private Paint ringPaint;
    private Paint eraserPaint;
    private boolean isSelected;
    private int dotRadius = smallDotRadius;


    public ColorPickerDotView(Context context) {
        this(context, null);
    }

    public ColorPickerDotView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerDotView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initDot();
    }

    private void initDot() {
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            eraserPaint.setColor(getResources().getColor(R.color.draw_disabled));
        } else {
            eraserPaint.setColor(getResources().getColor(R.color.draw_disabled, getContext().getTheme()));
        }
    }

    public void setColor(int color) {
        circlePaint.setColor(color);
        ringPaint.setColor(Color.WHITE);
        invalidate();
    }

    @Override
    public void setSelected(int colorPickerDotSize) {
        //if selected, increase size
        if (isSelected) {
            dotRadius = getNextDotSize(dotRadius);
        } else {
            dotRadius = colorPickerDotSize;
            isSelected = true;
        }
        invalidate();
    }

    @Override
    public void setUnselected() {
        isSelected = false;
        dotRadius = smallDotRadius;
        invalidate();
    }

    @Override
    public int getSize() {
        return dotRadius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isSelected) {
            drawSelectedRing(canvas);
        }
        int whiteColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            whiteColor = getResources().getColor(R.color.draw_white);
        } else {
            whiteColor = getResources().getColor(R.color.draw_white, getContext().getTheme());
        }
        if (circlePaint.getColor() == whiteColor) {
            //white dot is wrapped in grey
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, dotRadius, eraserPaint);
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, dotRadius - selectedRingSize, ringPaint);
        } else {
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, dotRadius, circlePaint);
        }
    }

    private void drawSelectedRing(Canvas canvas) {
        int ringSize = getRingSize(dotRadius);
        int whiteColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            whiteColor = getResources().getColor(R.color.draw_white);
        } else {
            whiteColor = getResources().getColor(R.color.draw_white, getContext().getTheme());
        }
        if (circlePaint.getColor() == whiteColor) {
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, ringSize, eraserPaint);
        } else {
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, ringSize, circlePaint);
        }
        canvas.drawCircle(getWidth() / 2, getHeight() / 2, ringSize - selectedRingSize, ringPaint);
    }

    public int getCircleColor() {
        return circlePaint.getColor();
    }

    public int getStrokeSize() {
        return dotRadius * 2;
    }

    private int getNextDotSize(int startingSize) {
        if (smallDotRadius == startingSize) {
            return mediumDotRadius;
        } else if (mediumDotRadius == startingSize) {
            return largeDotRadius;
        } else if (largeDotRadius == startingSize) {
            return smallDotRadius;
        }
        return smallDotRadius;
    }

    private int getRingSize(int startingSize) {
        if (smallDotRadius == startingSize) {
            return smallRingRadius;
        } else if (mediumDotRadius == startingSize) {
            return mediumRingRadius;
        } else if (largeDotRadius == startingSize) {
            return largeRingRadius;
        }
        return smallDotRadius;
    }

}
