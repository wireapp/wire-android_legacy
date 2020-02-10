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
package com.waz.zclient.utils;

import android.content.pm.ActivityInfo;

public enum SquareOrientation {
    NONE(0, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT_STRAIGHT(0, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    PORTRAIT_UPSIDE_DOWN(180, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    LANDSCAPE_LEFT(270, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_RIGHT(90, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

    public final int orientation;
    public final int activityOrientation;

    SquareOrientation(int orientation, int activityOrientation) {
        this.orientation = orientation;
        this.activityOrientation = activityOrientation;
    }
}
