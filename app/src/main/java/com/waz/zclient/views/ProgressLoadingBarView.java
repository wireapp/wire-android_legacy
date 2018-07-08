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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.R;

public class ProgressLoadingBarView extends View {
    private static final int DEFAULT_LOADING_BAR_COLOR = Color.RED;

    private Paint loadingBarPaint;
    private int strokeWidth;

    private float progress;

    public ProgressLoadingBarView(Context context) {
        super(context);
        init();
    }

    public ProgressLoadingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressLoadingBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = getResources().getDimensionPixelSize(R.dimen.loading_bar__stroke_width);
        loadingBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        loadingBarPaint.setColor(DEFAULT_LOADING_BAR_COLOR);
        loadingBarPaint.setStyle(Paint.Style.STROKE);
        loadingBarPaint.setStrokeWidth(strokeWidth);
        setAlpha(0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null) {
            return;
        }
        canvas.drawLine(0, strokeWidth / 2, progress * canvas.getWidth(), strokeWidth / 2, loadingBarPaint);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    public void setColor(int color) {
        loadingBarPaint.setColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animate().alpha(1);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().alpha(0);
    }
}
