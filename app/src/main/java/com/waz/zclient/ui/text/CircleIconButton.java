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
package com.waz.zclient.ui.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

public class CircleIconButton extends GlyphTextView {
    private static final int DEFAULT_EXTRA_RADIUS = 8;
    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final int DEFAULT_SELECTED_TEXT_COLOR = Color.RED;

    private int radius;
    private int centerX;
    private int centerY;
    private Paint circleColor;

    private int selectedTextColor = DEFAULT_SELECTED_TEXT_COLOR;
    private int textColor = DEFAULT_TEXT_COLOR;
    private int borderColor = DEFAULT_TEXT_COLOR;
    private int extraRadius;

    private int alphaFill;
    private int alphaStroke;

    private boolean showCircleBorder;

    public CircleIconButton(Context context) {
        this(context, null);
    }

    public CircleIconButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleIconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        // default is to show circle border
        showCircleBorder = true;

        circleColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        circleColor.setStyle(Paint.Style.FILL);
        circleColor.setColor(borderColor);
        circleColor.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.circle_icon_button_stroke));

        alphaFill = getResources().getInteger(R.integer.circle_icon_button__alpha_fill);
        alphaStroke = getResources().getInteger(R.integer.circle_icon_button__alpha_stroke);

        extraRadius = ViewUtils.toPx(getContext(), DEFAULT_EXTRA_RADIUS);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int actualWidth = canvas.getWidth() - getPaddingLeft() - getPaddingRight();
        int actualHeight = canvas.getHeight() - getPaddingBottom() - getPaddingTop();

        radius = Math.min(actualWidth, actualHeight) / 2 + extraRadius;
        centerX = actualWidth / 2 + getPaddingLeft();
        centerY = actualHeight / 2 + getPaddingTop();

        if (isSelected()) {
            circleColor.setStyle(Paint.Style.FILL);
            circleColor.setAlpha(alphaFill);
            canvas.drawCircle(centerX, centerY, radius, circleColor);
        } else {
            circleColor.setStyle(Paint.Style.STROKE);
            circleColor.setAlpha(alphaStroke);

            if (showCircleBorder) {
                canvas.drawCircle(centerX, centerY, radius, circleColor);
            }
        }


        super.onDraw(canvas);
    }

    public void setShowCircleBorder(boolean showCircleBorder) {
        this.showCircleBorder = showCircleBorder;
        invalidate();
    }

    public void setSelectedTextColor(int color) {
        selectedTextColor = color;
        updateTextColor();
    }

    public void setCircleColor(int color) {
        borderColor = color;
        circleColor.setColor(color);
        invalidate();
    }

    private void updateTextColor() {
        if (isSelected()) {
            setTextColor(selectedTextColor);
        } else {
            setTextColor(textColor);
        }

        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        updateTextColor();
    }
}
