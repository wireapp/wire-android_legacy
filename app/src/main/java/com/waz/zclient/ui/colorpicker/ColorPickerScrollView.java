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
import android.util.AttributeSet;
import android.view.View;

public class ColorPickerScrollView extends View {

    private Paint paint;
    private Paint darkPaint;
    private int leftX = 0;
    private int scrollBarSize = 0;
    private int maxScrollViewScroll = 0;

    public ColorPickerScrollView(Context context) {
        this(context, null);
    }

    public ColorPickerScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(Color.BLACK);
    }

    public void setLeftX(int scrollX) {
        double percentScrolled = (double) scrollX  / (double) maxScrollViewScroll;
        leftX = (int) (percentScrolled * (getWidth() - scrollBarSize));
        invalidate();
    }

    public void setScrollBarSize(int maxScroll) {
        //maxScroll = actual width of scrollview child
        if (maxScroll < getWidth()) {
            setVisibility(GONE);
            return;
        } else {
            setVisibility(VISIBLE);
        }
        this.maxScrollViewScroll = maxScroll - getWidth();
        scrollBarSize = (int) (((double) getWidth() / (double) maxScroll) * (double) getWidth());
        invalidate();
    }

    public void setScrollBarColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(leftX, 0, scrollBarSize + leftX, getHeight(), paint);
    }
}
