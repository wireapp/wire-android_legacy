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
package com.waz.zclient.pages.main.profile.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.utils.ViewUtils;

public class ConfirmationMenu extends LinearLayout {

    private static final int PRESSED_ALPHA = 180;
    private ConfirmationMenuListener confirmationMenuListener;
    private TextView confirmTextView;
    private TextView cancelTextView;
    private final OnClickListener onClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (confirmationMenuListener == null) {
                return;
            }
            switch (v.getId()) {
                case R.id.ttv__confirmation__cancel:
                    confirmationMenuListener.cancel();
                    break;
                case R.id.ttv__confirmation__confirm:
                    confirmationMenuListener.confirm();
                    break;
            }
        }
    };

    public ConfirmationMenu(Context context) {
        this(context, null);
    }

    public ConfirmationMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConfirmationMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initViews();
    }

    private void initViews() {
        LayoutInflater.from(getContext()).inflate(R.layout.confirmation_menu_layout, this, true);

        confirmTextView = ViewUtils.getView(this, R.id.ttv__confirmation__confirm);
        confirmTextView.setOnClickListener(onClickListener);

        cancelTextView = ViewUtils.getView(this, R.id.ttv__confirmation__cancel);
        cancelTextView.setOnClickListener(onClickListener);
    }

    private Drawable getButtonBackground(int borderColor, int fillColor, int strokeWidth, int cornerRadius) {
        int fillColorPressed = getPressColor(PRESSED_ALPHA, fillColor);
        int borderColorPressed = getPressColor(PRESSED_ALPHA, borderColor);

        GradientDrawable gradientDrawablePressed = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{fillColorPressed, fillColorPressed});
        gradientDrawablePressed.setStroke(strokeWidth, borderColorPressed);
        gradientDrawablePressed.setCornerRadius(cornerRadius);

        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{fillColor, fillColor});
        gradientDrawable.setStroke(strokeWidth, borderColor);
        gradientDrawable.setCornerRadius(cornerRadius);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed},
                        gradientDrawablePressed);
        states.addState(new int[]{android.R.attr.state_focused},
                        gradientDrawablePressed);
        states.addState(new int[] {}, gradientDrawable);

        return states;
    }

    private int getPressColor(int alpha, int borderColor) {
        int borderColorPressed;
        if (Color.alpha(borderColor) == 0) {
            borderColorPressed = borderColor;
        } else {
            borderColorPressed = ColorUtils.injectAlpha(alpha, borderColor);
        }
        return borderColorPressed;
    }

    public void setCancelColor(int textColor, int backgroundColor) {
        int strokeWidth = getResources().getDimensionPixelSize(R.dimen.framework_confirmation_menu_button_stroke_width);
        int cornerRadius = getResources().getDimensionPixelSize(R.dimen.framework_confirmation_menu_button_corner_radius);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            ViewUtils.setBackground(cancelTextView,
                getButtonBackground(backgroundColor,
                                    getResources().getColor(R.color.framework_confirmation_menu_background_color),
                                    strokeWidth,
                                    cornerRadius));
        } else {
            ViewUtils.setBackground(cancelTextView,
                getButtonBackground(backgroundColor,
                    getResources().getColor(R.color.framework_confirmation_menu_background_color, getContext().getTheme()),
                    strokeWidth,
                    cornerRadius));
        }

        cancelTextView.setTextColor(textColor);
    }

    public void setConfirmColor(int textColor, int backgroundColor) {
        int strokeWidth = getResources().getDimensionPixelSize(R.dimen.framework_confirmation_menu_button_stroke_width);
        int cornerRadius = getResources().getDimensionPixelSize(R.dimen.framework_confirmation_menu_button_corner_radius);
        ViewUtils.setBackground(confirmTextView, getButtonBackground(backgroundColor,
                                                                     backgroundColor,
                                                                     strokeWidth,
                                                                     cornerRadius));
        confirmTextView.setTextColor(textColor);
    }

    public void setWireTheme(OptionsTheme optionsTheme) {
        confirmTextView.setTextColor(optionsTheme.getTextColorPrimary());
    }

    public void setAccentColor(int color) {
        setConfirmColor(confirmTextView.getCurrentTextColor(), color);
        setCancelColor(cancelTextView.getCurrentTextColor(), color);
        cancelTextView.setTextColor(color);
    }

    public void setConfirmationMenuListener(ConfirmationMenuListener confirmationMenuListener) {
        this.confirmationMenuListener = confirmationMenuListener;
    }

    public void setCancel(String text) {
        cancelTextView.setText(text);
    }

    public void setConfirm(String text) {
        confirmTextView.setText(text);
    }

    public void setConfirmEnabled(boolean enabled) {
        confirmTextView.setEnabled(enabled);
    }
}
