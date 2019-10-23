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
package com.waz.zclient.ui.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.waz.zclient.utils.ViewUtils;

public class ColorUtils {
    private static final int ROUNDED_TEXT_BOX_BACK_ALPHA = 163;

    private ColorUtils() {
    }

    public static int injectAlpha(int alpha, int color) {
        return androidx.core.graphics.ColorUtils.setAlphaComponent(color, alpha);
    }

    public static int injectAlpha(float alpha, int color) {
        return androidx.core.graphics.ColorUtils.setAlphaComponent(color, (int) (255 * alpha));
    }


    public static ColorStateList createButtonTextColorStateList(int[] colors) {
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_enabled}, {-android.R.attr.state_enabled}};
        return new ColorStateList(states, colors);
    }

    public static int adjustBrightness(int color, float percentage) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * percentage), (int) (Color.green(color) * percentage), (int) (Color.blue(color) * percentage));
    }


    public static Drawable getTintedDrawable(Context context, int resId, int color) {
        Drawable drawable = DrawableCompat.wrap(ContextCompat.getDrawable(context, resId));
        DrawableCompat.setTint(drawable.mutate(), color);
        return drawable;
    }

    public static Drawable getRoundedTextBoxBackground(Context context, int color, int targetHeight) {
        GradientDrawable drawable = new GradientDrawable();
        color = injectAlpha(ROUNDED_TEXT_BOX_BACK_ALPHA, color);
        drawable.setColor(color);
        drawable.setCornerRadius(ViewUtils.toPx(context, targetHeight / 2));
        return drawable;
    }

    public static Drawable getTransparentDrawable() {
        ColorDrawable drawable = new ColorDrawable();
        drawable.setColor(Color.TRANSPARENT);
        return drawable;
    }

}
