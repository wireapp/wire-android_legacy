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
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

public class CursorUtils {

    public static final int NUM_CURSOR_ROW_BUTTONS = 6;

    public static int getMarginBetweenCursorButtons(Context context) {
        int cursorButtonWidth = context.getResources().getDimensionPixelSize(R.dimen.new_cursor_menu_button_width);
        int paddingEdge = context.getResources().getDimensionPixelSize(R.dimen.cursor_toolbar_padding_horizontal_edge);
        int total = ViewUtils.getOrientationIndependentDisplayWidth(context) - 2 * paddingEdge - cursorButtonWidth * NUM_CURSOR_ROW_BUTTONS;
        return total / (NUM_CURSOR_ROW_BUTTONS - 1);
    }

    public static int getDistanceOfAudioMessageIconToLeftScreenEdge(Context context) {
        int cursorToolbarMarginRight = context.getResources().getDimensionPixelSize(R.dimen.cursor_toolbar_padding_horizontal_edge);
        int cursorButtonWidth = context.getResources().getDimensionPixelSize(R.dimen.new_cursor_menu_button_width);
        int cursorButtonMarginRight = getMarginBetweenCursorButtons(context);

        return cursorButtonWidth + cursorButtonMarginRight + cursorToolbarMarginRight;
    }
}
