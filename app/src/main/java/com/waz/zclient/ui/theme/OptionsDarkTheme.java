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
package com.waz.zclient.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.waz.zclient.R;

public class OptionsDarkTheme implements OptionsTheme {
    private Resources resource;
    private Resources.Theme theme;

    public OptionsDarkTheme(Context context) {
        resource = context.getResources();
        theme = context.getTheme();
    }

    @Override
    public Type getType() {
        return Type.DARK;
    }

    @Override
    public int getTextColorPrimary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            return resource.getColor(R.color.text__primary_dark);
        } else {
            return resource.getColor(R.color.text__primary_dark, theme);
        }
    }

    @Override
    public ColorStateList getTextColorPrimarySelector() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            return resource.getColorStateList(R.color.wire__text_color_primary_dark_selector);
        } else {
            return resource.getColorStateList(R.color.wire__text_color_primary_dark_selector, theme);
        }
    }

    @Override
    public int getOverlayColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            return resource.getColor(R.color.background_overlay_dark);
        } else {
            return resource.getColor(R.color.background_overlay_dark, theme);
        }
    }

    @Override
    public void tearDown() {
        resource = null;
    }

    @Override
    public int getCheckboxTextColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            return resource.getColor(R.color.text__primary_light);
        } else {
            return resource.getColor(R.color.text__primary_light, theme);
        }
    }

    @Override
    public Drawable getCheckBoxBackgroundSelector() {
        return resource.getDrawable(R.drawable.selector__check_box__background__dark);
    }

    @Override
    public ColorStateList getIconButtonTextColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            return resource.getColorStateList(R.color.selector__icon_button__text_color__dark);
        } else {
            return resource.getColorStateList(R.color.selector__icon_button__text_color__dark, theme);
        }
    }

    @Override
    public Drawable getIconButtonBackground() {
        return resource.getDrawable(R.drawable.selector__icon_button__background__dark);
    }
}
