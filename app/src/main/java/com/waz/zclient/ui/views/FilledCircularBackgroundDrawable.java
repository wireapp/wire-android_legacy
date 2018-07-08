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
package com.waz.zclient.ui.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;

public class FilledCircularBackgroundDrawable extends Drawable {
    private final int radius;
    private final Paint paint;
    private int alpha = 255;

    public FilledCircularBackgroundDrawable(@ColorInt int color, int diameter) {
        this.radius = diameter / 2;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(alpha);
        paint.setColor(color);
    }

    public FilledCircularBackgroundDrawable(@ColorInt int color) {
        this(color, -1);
    }

    @Override
    public void draw(Canvas canvas) {
        final int width = getBounds().width();
        final int height = getBounds().height();
        if (radius > 0) {
            canvas.drawCircle(width / 2, height / 2, radius, paint);
        } else {
            canvas.drawCircle(width / 2, height / 2, Math.min(width, height) / 2, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha == this.alpha) {
            return;
        }
        this.alpha = alpha;
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
