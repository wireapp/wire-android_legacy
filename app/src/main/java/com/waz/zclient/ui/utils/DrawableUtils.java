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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import timber.log.Timber;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


// From support design Lib
public class DrawableUtils {

    private static Method setConstantStateMethod;
    private static boolean setConstantStateMethodFetched;

    private static Field drawableContainerStateField;
    private static boolean drawableContainerStateFieldFetched;

    private DrawableUtils() {}

    public static boolean setContainerConstantState(DrawableContainer drawable,
                                             Drawable.ConstantState constantState) {
        return setContainerConstantStateV9(drawable, constantState);
    }

    private static boolean setContainerConstantStateV9(DrawableContainer drawable,
                                                       Drawable.ConstantState constantState) {
        if (!setConstantStateMethodFetched) {
            try {
                setConstantStateMethod = DrawableContainer.class.getDeclaredMethod(
                    "setConstantState", DrawableContainer.DrawableContainerState.class);
                setConstantStateMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                Timber.e(e, "Could not fetch setConstantState(). Oh well.");
            }
            setConstantStateMethodFetched = true;
        }
        if (setConstantStateMethod != null) {
            try {
                setConstantStateMethod.invoke(drawable, constantState);
                return true;
            } catch (Exception e) {
                Timber.e(e, "Could not invoke setConstantState(). Oh well.");
            }
        }
        return false;
    }

    // Nasty hack - for some reason the normal drawable was not showing up in the preferences
    public static Drawable drawableToBitmapDrawable(Resources resources, Drawable drawable, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(canvas);
        return new BitmapDrawable(resources, bitmap);
    }
}
