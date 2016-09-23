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
package com.waz.zclient.ui.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import com.waz.zclient.ui.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.ColorUtils;

public class CursorIconButton extends GlyphTextView {

    private static final float PRESSED_ALPHA__LIGHT = 0.32f;
    private static final float PRESSED_ALPHA__DARK = 0.40f;

    private static final float TRESHOLD = 0.55f;
    private static final float DARKEN_FACTOR = 0.1f;
    private float alphaPressed;

    public CursorIconButton(Context context) {
        this(context, null);
    }

    public CursorIconButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorIconButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAccentColor(int color) {
        if (ThemeUtils.isDarkTheme(getContext())) {
            alphaPressed = PRESSED_ALPHA__DARK;
        } else {
            alphaPressed = PRESSED_ALPHA__LIGHT;
        }

        float avg = (Color.red(color) + Color.blue(color) + Color.green(color)) / (3 * 255.0f);
        if (avg > TRESHOLD) {
            float darken = 1.0f - DARKEN_FACTOR;
            color = Color.rgb((int) (Color.red(color) * darken),
                              (int) (Color.green(color) * darken),
                              (int) (Color.blue(color) * darken));
        }

        int pressed = ColorUtils.injectAlpha(alphaPressed, color);
        GradientDrawable pressedTextColor = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                                                                 new int[] {pressed, pressed});
        pressedTextColor.setShape(GradientDrawable.OVAL);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressedTextColor);
        states.addState(new int[] {android.R.attr.state_focused}, pressedTextColor);
        states.addState(new int[] {-android.R.attr.state_enabled}, pressedTextColor);
        states.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));

        setBackground(states);

        setTextColor(color);
        invalidate();
    }

    public void setTextColor(int accentColor) {
        int pressedColor = getResources().getColor(R.color.text__primary_dark_40);
        int focusedColor = pressedColor;
        int selectedColor = accentColor;
        int enabledColor = getResources().getColor(R.color.text__primary_dark);
        int disabledColor = getResources().getColor(R.color.text__primary_dark_16);

        if (!ThemeUtils.isDarkTheme(getContext())) {
            pressedColor = getResources().getColor(R.color.text__primary_light__40);
            focusedColor = pressedColor;
            enabledColor = getResources().getColor(R.color.text__primary_light);
            disabledColor = getResources().getColor(R.color.text__primary_light_16);
        }

        int[] colors = {pressedColor, focusedColor, selectedColor, enabledColor, disabledColor};
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_selected}, {android.R.attr.state_enabled}, {-android.R.attr.state_enabled}};
        ColorStateList colorStateList = new ColorStateList(states, colors);

        super.setTextColor(colorStateList);
    }

    public void setAccentTextColor(int color) {
        super.setTextColor(color);
    }
}
