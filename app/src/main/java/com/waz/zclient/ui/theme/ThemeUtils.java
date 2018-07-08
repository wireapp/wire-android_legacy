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
import android.util.TypedValue;
import com.waz.zclient.R;
import com.waz.zclient.ui.utils.ResourceUtils;

public class ThemeUtils {
    private ThemeUtils() {}

    // Hack to get current theme - Android doesn't have a mechanism to do this properly
    // This should only be used for historical reasons. New Views should always be themed
    // according to Android Theme Functionality.
    public static boolean isDarkTheme(Context context) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.themeName, outValue, true);
        return "dark".equals(outValue.string);
    }

    public static float getEphemeralBackgroundAlpha(Context context) {
        return isDarkTheme(context) ?
               ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__primary_alpha__dark_theme) :
               ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__primary_alpha);
    }
}
