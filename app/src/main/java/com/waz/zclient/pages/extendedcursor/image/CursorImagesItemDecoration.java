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
package com.waz.zclient.pages.extendedcursor.image;


import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class CursorImagesItemDecoration extends RecyclerView.ItemDecoration {
    private static final int NUM_GRID_ROW_ITEMS = 3;
    private int spacing;

    public CursorImagesItemDecoration(int spacing) {
        this.spacing = spacing;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (parent.getChildAdapterPosition(view) == 0) {
            return;
        }

        outRect.left = spacing;

        if ((parent.getChildAdapterPosition(view) - 1) % NUM_GRID_ROW_ITEMS == 0) {
            return;
        }
        outRect.top = spacing;
    }
}
