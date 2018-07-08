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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.AttributeSet;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.utils.ViewUtils;

public class ZetaButton extends TypefaceTextView {
    // Optional callback for state changes

    private int accentColor;
    private boolean isFilled = true;

    public ZetaButton(Context context) {
        this(context, null);
    }

    public ZetaButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZetaButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        if (attrs != null) {
            TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ZetaButton, 0, 0);
            setIsFilled(array.getBoolean(R.styleable.ZetaButton_isFilled, isFilled));
        }
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        super.setEnabled(isEnabled);

        if (isEnabled) {
            setAlpha(1);
            setClickable(true);
        } else {
            float disabledAlpha = ResourceUtils.getResourceFloat(getResources(),
                                                                 R.dimen.button__disabled_state__alpha);
            setAlpha(disabledAlpha);
            setClickable(false);
        }
    }

    @Override
    public void setTextColor(int color) {
        int pressedTextColor = getPressedColor(color);
        int[] textColors = {pressedTextColor, pressedTextColor, color, pressedTextColor};
        super.setTextColor(ColorUtils.createButtonTextColorStateList(textColors));
    }

    public void setIsFilled(boolean isFilled) {
        this.isFilled = isFilled;
    }

    public void setAccentColor(int color, boolean keepTextColor) {
        if (accentColor == color) {
            return;
        }
        accentColor = color;
        int strokeWidth = getResources().getDimensionPixelSize(R.dimen.button__stroke_width);
        int cornerRadius = getResources().getDimensionPixelSize(R.dimen.button__corner_radius);
        int fillColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            fillColor = isFilled ?
                accentColor :
                getResources().getColor(R.color.transparent);
        } else {
            fillColor = isFilled ?
                accentColor :
                getResources().getColor(R.color.transparent, getContext().getTheme());
        }
        ViewUtils.setBackground(this, getButtonBackground(accentColor, fillColor, strokeWidth, cornerRadius));

        if (!isFilled && !keepTextColor) {
            setTextColor(color);
        }
    }

    public void setAccentColor(int color) {
        setAccentColor(color, false);
    }

    private void init() {
        setClickable(true);
        int pressedTextColor = getPressedColor(getCurrentTextColor());
        int[] textColors = {pressedTextColor, pressedTextColor, getCurrentTextColor(), pressedTextColor};
        this.setTextColor(ColorUtils.createButtonTextColorStateList(textColors));
    }

    private int getPressedColor(int originalColor) {
        float pressedAlpha = ResourceUtils.getResourceFloat(getResources(), R.dimen.button__pressed_state__alpha);

        // Dim color = apply black black overlay with PRESSED_ALPHA
        int red = (int) (Color.red(originalColor) * (1 - pressedAlpha));
        int green = (int) (Color.green(originalColor) * (1 - pressedAlpha));
        int blue = (int) (Color.blue(originalColor) * (1 - pressedAlpha));

        return Color.argb(255, red, green, blue);
    }

    private Drawable getButtonBackground(int borderColor, int fillColor, int strokeWidth, int cornerRadius) {
        int fillColorPressed = getPressedColor(fillColor);
        int borderColorPressed = getPressedColor(borderColor);

        if (!isFilled) {
            fillColorPressed = fillColor;
        }

        GradientDrawable gradientDrawablePressed = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{fillColorPressed, fillColorPressed});
        gradientDrawablePressed.setStroke(strokeWidth, borderColorPressed);
        gradientDrawablePressed.setCornerRadius(cornerRadius);

        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{fillColor, fillColor});
        gradientDrawable.setStroke(strokeWidth, borderColor);
        gradientDrawable.setCornerRadius(cornerRadius);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, gradientDrawablePressed);
        states.addState(new int[]{android.R.attr.state_focused}, gradientDrawablePressed);
        states.addState(new int[]{-android.R.attr.state_enabled}, gradientDrawablePressed);
        states.addState(new int[]{}, gradientDrawable);

        return states;
    }

}
