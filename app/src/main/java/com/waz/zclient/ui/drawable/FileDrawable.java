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
package com.waz.zclient.ui.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.waz.zclient.R;
import com.waz.zclient.ui.utils.TypefaceUtils;

import java.util.Locale;

public class FileDrawable extends Drawable {

    private final int textCorrectionSpacing; //to align the text nicely within the glyph bounds

    private final String fileGlyph;
    private final String extension;

    private final Paint glyphPaint = new Paint();
    private final Paint textPaint = new Paint();

    public FileDrawable(Context context, String extension) {
        this.fileGlyph = context.getResources().getString(R.string.glyph__file);
        this.extension = extension;
        this.textCorrectionSpacing = context.getResources().getDimensionPixelSize(R.dimen.wire__padding__4);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            glyphPaint.setColor(context.getResources().getColor(R.color.black_48));
            //noinspection deprecation
            textPaint.setColor(context.getResources().getColor(R.color.white));
        } else {
            glyphPaint.setColor(context.getResources().getColor(R.color.black_48, context.getTheme()));
            textPaint.setColor(context.getResources().getColor(R.color.white, context.getTheme()));
        }

        glyphPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName()));
        glyphPaint.setAntiAlias(true);
        glyphPaint.setTextAlign(Paint.Align.CENTER);
        glyphPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.content__audio_message__button__size));

        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__tiny));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawText(fileGlyph, getBounds().width() / 2, getBounds().height(), glyphPaint);
        if (extension != null) {
            canvas.drawText(extension.toUpperCase(Locale.getDefault()),
                            getBounds().width() / 2,
                            getBounds().height() - textCorrectionSpacing,
                            textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

}
