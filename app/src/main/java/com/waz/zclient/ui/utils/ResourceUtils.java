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
import android.content.res.Resources;
import android.support.annotation.DimenRes;
import android.util.TypedValue;

import com.waz.utils.crypto.ZSecureRandom;
import com.waz.zclient.R;


public class ResourceUtils {
    public static final String TAG = ResourceUtils.class.getName();

    private ResourceUtils() {
    }

    /**
     * returns a float resource with the following structure
     * <p/>
     * <item name="resId" type="dimen" format="float"></item>
     */
    public static float getResourceFloat(Resources res, @DimenRes int resId) {
        TypedValue typedValue = new TypedValue();
        res.getValue(resId, typedValue, true);
        return typedValue.getFloat();
    }

    public static int getRandomAccentColor(Context context) {
        int[] validAccentColors = context.getResources().getIntArray(R.array.selectable_accents_color);
        int accentColorPos = ZSecureRandom.nextInt(0, validAccentColors.length - 1);
        return validAccentColors[accentColorPos];
    }
}
