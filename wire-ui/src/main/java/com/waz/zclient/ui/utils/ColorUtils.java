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
package com.waz.zclient.ui.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import com.waz.zclient.utils.ViewUtils;

public class ColorUtils {
    private static final int PRESSED_ALPHA = 180;
    private static final int ROUNDED_TEXT_BOX_BACK_ALPHA = 163;

    private ColorUtils() {
    }

    public static int injectAlpha(int alpha, int color) {
        return android.support.v4.graphics.ColorUtils.setAlphaComponent(color, alpha);
    }

    public static int injectAlpha(float alpha, int color) {
        return android.support.v4.graphics.ColorUtils.setAlphaComponent(color, (int) (255 * alpha));
    }


    public static ColorStateList createButtonTextColorStateList(int[] colors) {
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_enabled}, {-android.R.attr.state_enabled}};
        return new ColorStateList(states, colors);
    }

    public static int adjustBrightness(int color, float percentage) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * percentage), (int) (Color.green(color) * percentage), (int) (Color.blue(color) * percentage));
    }


    public static int getPressColor(int alpha, int borderColor) {
        int borderColorPressed;
        if (Color.alpha(borderColor) == 0) {
            borderColorPressed = borderColor;
        } else {
            borderColorPressed = ColorUtils.injectAlpha(alpha, borderColor);
        }
        return borderColorPressed;
    }

    public static Drawable getTintedDrawable(Context context, int resId, int color) {
        Drawable drawable = DrawableCompat.wrap(ContextCompat.getDrawable(context, resId));
        DrawableCompat.setTint(drawable.mutate(), color);
        return drawable;
    }

    public static Drawable getButtonBackground(int borderColor, int fillColor, int strokeWidth, int cornerRadius) {
        int fillColorPressed = getPressColor(PRESSED_ALPHA, fillColor);
        int borderColorPressed = getPressColor(PRESSED_ALPHA, borderColor);

        GradientDrawable gradientDrawablePressed = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                                        new int[] {fillColorPressed, fillColorPressed});
        gradientDrawablePressed.setStroke(strokeWidth, borderColorPressed);
        gradientDrawablePressed.setCornerRadius(cornerRadius);

        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                                 new int[] {fillColor, fillColor});
        gradientDrawable.setStroke(strokeWidth, borderColor);
        gradientDrawable.setCornerRadius(cornerRadius);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                        gradientDrawablePressed);
        states.addState(new int[] {android.R.attr.state_focused},
                        gradientDrawablePressed);
        states.addState(new int[] {}, gradientDrawable);

        return states;
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

    public static void adjustBrightness(ColorMatrix cm, float value) {
        value = Math.min(100, Math.max(value, -100));
        float[] mat = new float[] {
            1, 0, 0, 0, value,
            0, 1, 0, 0, value,
            0, 0, 1, 0, value,
            0, 0, 0, 1, 0,
            0, 0, 0, 0, 1
        };
        cm.postConcat(new ColorMatrix(mat));
    }
}
