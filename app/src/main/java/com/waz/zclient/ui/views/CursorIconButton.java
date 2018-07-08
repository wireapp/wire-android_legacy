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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.AttributeSet;
import com.waz.zclient.R;
import com.waz.zclient.ui.cursor.CursorMenuItem;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.ColorUtils;

public class CursorIconButton extends GlyphTextView {

    private static final float PRESSED_ALPHA__LIGHT = 0.32f;
    private static final float PRESSED_ALPHA__DARK = 0.40f;

    private static final float TRESHOLD = 0.55f;
    private static final float DARKEN_FACTOR = 0.1f;
    private float alphaPressed;
    private CursorMenuItem cursorMenuItem;

    public CursorIconButton(Context context) {
        this(context, null);
    }

    public CursorIconButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorIconButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCursorMenuItem(CursorMenuItem cursorMenuItem) {
        this.cursorMenuItem = cursorMenuItem;
        setText(cursorMenuItem.glyphResId);
    }

    public CursorMenuItem getCursorMenuItem() {
        return cursorMenuItem;
    }

    public void showEphemeralMode(int color) {
        setTextColor(color);
        if (cursorMenuItem != null) {
            setText(cursorMenuItem.timedGlyphResId);
        }
    }

    public void hideEphemeralMode(int color) {
        setTextColor(color);
        if (cursorMenuItem != null) {
            setText(cursorMenuItem.glyphResId);
        }
    }

    public void setPressedBackgroundColor(int color) {
        setBackgroundColor(Color.TRANSPARENT, color);
    }

    public void setSolidBackgroundColor(int color) {
        setBackgroundColor(color, color);
    }

    private void setBackgroundColor(int defaultColor, int pressedColor) {
        if (ThemeUtils.isDarkTheme(getContext())) {
            alphaPressed = PRESSED_ALPHA__DARK;
        } else {
            alphaPressed = PRESSED_ALPHA__LIGHT;
        }

        float avg = (Color.red(pressedColor) + Color.blue(pressedColor) + Color.green(pressedColor)) / (3 * 255.0f);
        if (avg > TRESHOLD) {
            float darken = 1.0f - DARKEN_FACTOR;
            pressedColor = Color.rgb((int) (Color.red(pressedColor) * darken),
                              (int) (Color.green(pressedColor) * darken),
                              (int) (Color.blue(pressedColor) * darken));
        }

        int pressed = ColorUtils.injectAlpha(alphaPressed, pressedColor);
        GradientDrawable pressedBgColor = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                                                                 new int[] {pressed, pressed});
        pressedBgColor.setShape(GradientDrawable.OVAL);

        GradientDrawable defaultBgColor = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                                                                 new int[] {defaultColor, defaultColor});
        defaultBgColor.setShape(GradientDrawable.OVAL);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressedBgColor);
        states.addState(new int[] {android.R.attr.state_focused}, pressedBgColor);
        states.addState(new int[] {-android.R.attr.state_enabled}, pressedBgColor);
        states.addState(new int[] {}, defaultBgColor);

        setBackground(states);

        invalidate();
    }

    public void initTextColor(int selectedColor) {
        int pressedColor;
        int focusedColor;
        int enabledColor;
        int disabledColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            pressedColor = getResources().getColor(R.color.text__primary_dark_40);
            focusedColor = pressedColor;
            //noinspection deprecation
            enabledColor = getResources().getColor(R.color.text__primary_dark);
            //noinspection deprecation
            disabledColor = getResources().getColor(R.color.text__primary_dark_16);

            if (!ThemeUtils.isDarkTheme(getContext())) {
                //noinspection deprecation
                pressedColor = getResources().getColor(R.color.text__primary_light__40);
                focusedColor = pressedColor;
                //noinspection deprecation
                enabledColor = getResources().getColor(R.color.text__primary_light);
                //noinspection deprecation
                disabledColor = getResources().getColor(R.color.text__primary_light_16);
            }
        } else {
            pressedColor = getResources().getColor(R.color.text__primary_dark_40, getContext().getTheme());
            focusedColor = pressedColor;
            enabledColor = getResources().getColor(R.color.text__primary_dark, getContext().getTheme());
            disabledColor = getResources().getColor(R.color.text__primary_dark_16, getContext().getTheme());

            if (!ThemeUtils.isDarkTheme(getContext())) {
                pressedColor = getResources().getColor(R.color.text__primary_light__40, getContext().getTheme());
                focusedColor = pressedColor;
                enabledColor = getResources().getColor(R.color.text__primary_light, getContext().getTheme());
                disabledColor = getResources().getColor(R.color.text__primary_light_16, getContext().getTheme());
            }
        }

        int[] colors = {pressedColor, focusedColor, selectedColor, enabledColor, disabledColor};
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_selected}, {android.R.attr.state_enabled}, {-android.R.attr.state_enabled}};
        ColorStateList colorStateList = new ColorStateList(states, colors);

        super.setTextColor(colorStateList);
    }
}
