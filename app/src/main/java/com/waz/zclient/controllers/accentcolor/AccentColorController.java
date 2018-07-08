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
package com.waz.zclient.controllers.accentcolor;

import android.content.Context;
import com.waz.api.AccentColor;
import com.waz.api.impl.AccentColors;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.ui.utils.ResourceUtils;

import java.util.HashSet;
import java.util.Set;

public class AccentColorController implements IAccentColorController {
    public static final String TAG = AccentColorController.class.getName();
    private static final int NO_COLOR_FOUND = -1;

    private final int[] originalAccentColors;
    private final int[] uiAccentColors;

    private Set<AccentColorObserver> accentColorObservers = new HashSet<>();

    private int color;

    public AccentColorController(Context context, IUserPreferencesController userPreferencesController) {
        originalAccentColors = context.getResources().getIntArray(R.array.original_accents_color);
        uiAccentColors = context.getResources().getIntArray(R.array.selectable_accents_color);
        color = userPreferencesController.getLastAccentColor();

        if (color == NO_COLOR_FOUND || !isSelectableColor(color)) {
            color = ResourceUtils.getRandomAccentColor(context);
            userPreferencesController.setLastAccentColor(color);
        }
    }

    @Override
    public void addAccentColorObserver(AccentColorObserver accentColorObserver) {
        accentColorObservers.add(accentColorObserver);
        accentColorObserver.onAccentColorHasChanged(color);
    }

    @Override
    public void removeAccentColorObserver(AccentColorObserver accentColorObserver) {
        accentColorObservers.remove(accentColorObserver);
    }

    @Override
    public AccentColor getAccentColor() {
        AccentColor[] colors = AccentColors.getColors();
        for (AccentColor color : colors) {
            if (color.getColor() == this.color) {
                return color;
            }
        }
        return AccentColors.defaultColor();
    }

    @Override
    public int getColor() {
        return color;
    }

    @Override
    public void setColor(int color) {
        if (isValidColor(color)) {
            notifyAccentColorHasChanged(color);
            this.color = color;
        } else if (BuildConfig.DEBUG) {
            throw new RuntimeException("Couldn't find predefined accent color: " + color);
        }
    }

    @Override
    public void tearDown() {
        accentColorObservers.clear();
    }

    private void notifyAccentColorHasChanged(int color) {
        for (AccentColorObserver accentColorObserver : accentColorObservers) {
            accentColorObserver.onAccentColorHasChanged(color);
        }
    }

    private boolean isValidColor(int color) {
        for (int accentColor : originalAccentColors) {
            if (accentColor == color) {
                return true;
            }
        }
        return false;
    }

    private boolean isSelectableColor(int color) {
        for (int accentColor : uiAccentColors) {
            if (accentColor == color) {
                return true;
            }
        }
        return false;
    }
}
