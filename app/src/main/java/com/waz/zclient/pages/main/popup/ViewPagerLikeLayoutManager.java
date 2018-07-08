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
package com.waz.zclient.pages.main.popup;

import android.content.Context;
import android.graphics.PointF;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class ViewPagerLikeLayoutManager extends LinearLayoutManager {

    private final LinearSmoothScroller linearSmoothScroller;

    public ViewPagerLikeLayoutManager(Context context) {
        this(context, false);
    }

    public ViewPagerLikeLayoutManager(Context context, boolean reverseLayout) {
        super(context, HORIZONTAL, reverseLayout);
        linearSmoothScroller = new LinearSmoothScroller(context) {
            protected int getHorizontalSnapPreference() {
                return SNAP_TO_START;
            }

            protected int getVerticalSnapPreference() {
                return SNAP_TO_START;
            }

            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                return ViewPagerLikeLayoutManager.this.computeScrollVectorForPosition(targetPosition);
            }
        };
    }

    public int getPositionForVelocity(int velocity) {
        if (getChildCount() == 0) {
            return 0;
        }
        return calcPosForVelocity(velocity, getPosition(getChildAt(0)));
    }

    private int calcPosForVelocity(int velocity, int currPos) {
        if (velocity < 0) {
            return Math.max(currPos, 0);
        } else {
            return currPos + 1;
        }
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    public int getFixScrollPos() {
        if (this.getChildCount() == 0) {
            return 0;
        }

        final View child = getChildAt(0);
        final int childPos = getPosition(child);

        if (Math.abs(child.getLeft()) > child.getMeasuredWidth() / 2) {
            return childPos + 1;
        }
        return childPos;
    }
}
