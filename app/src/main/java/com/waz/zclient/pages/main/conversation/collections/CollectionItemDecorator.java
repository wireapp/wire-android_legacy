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
package com.waz.zclient.pages.main.conversation.collections;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.waz.zclient.collection.adapters.CollectionAdapter;
import com.waz.zclient.collection.adapters.Header;
import com.waz.zclient.collection.adapters.HeaderId;

import java.util.HashMap;
import java.util.Map;

public class CollectionItemDecorator extends RecyclerView.ItemDecoration {

    private CollectionAdapter adapter;
    private int spanCount;
    private Map<Integer, Rect> headerPositions;

    public CollectionItemDecorator(CollectionAdapter adapter, int spanCount) {
        headerPositions = new HashMap<>();
        this.adapter = adapter;
        this.spanCount = spanCount;
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(c, parent, state);
        headerPositions.clear();
        final int childCount = parent.getChildCount();
        if (childCount <= 0 || adapter.getItemCount() <= 0) {
            return;
        }

        int highestTop = Integer.MAX_VALUE;

        Rect tempRect = new Rect();
        for (int i = childCount - 1; i >= 0; i--) {
            View itemView = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(itemView);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            if (i == 0 || isFirstUnderHeader(position)) {
                View header = adapter.getHeaderView(parent, position);
                int translationX = 0;
                int translationY = itemView.getTop() - header.getHeight();
                tempRect.set(translationX, translationY, translationX + header.getWidth(),
                             translationY + header.getHeight());
                if (tempRect.bottom > highestTop) {
                    tempRect.offset(0, highestTop - tempRect.bottom);
                }
                drawHeader(c, header, tempRect);
                highestTop = tempRect.top;
                headerPositions.put(position, new Rect(tempRect));
            }
        }
    }

    public int getHeaderClicked(int x, int y) {
        for (int position : headerPositions.keySet()) {
            if (headerPositions.get(position).contains(x, y)) {
                return position;
            }
        }
        return -1;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        int itemPosition = parent.getChildAdapterPosition(view);
        if (itemPosition == RecyclerView.NO_POSITION) {
            return;
        }
        boolean underHeader = isUnderHeader(itemPosition);
        if (underHeader) {
            View header = adapter.getHeaderView(parent, itemPosition);
            outRect.top = header.getHeight();
        }
    }

    private boolean isUnderHeader(int itemPosition) {
        return isUnderHeader(itemPosition, spanCount);
    }

    private boolean isUnderHeader(int itemPosition, int spanCount) {
        if (itemPosition == 0) {
            return true;
        }
        spanCount = adapter.isFullSpan(itemPosition) ? 1 : spanCount;
        HeaderId headerId = adapter.getHeaderId(itemPosition);
        for (int i = 1; i < spanCount + 1; i++) {
            HeaderId previousHeaderId = Header.invalid();
            int previousItemPosition = itemPosition - i;
            if (previousItemPosition >= 0 && previousItemPosition < adapter.getItemCount()) {
                previousHeaderId = adapter.getHeaderId(previousItemPosition);
            }
            if (!headerId.equals(previousHeaderId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstUnderHeader(int position) {
        return position == 0 || !adapter.getHeaderId(position).equals(adapter.getHeaderId(position - 1));
    }

    private void drawHeader(Canvas canvas, View header, Rect offset) {
        canvas.save();
        canvas.translate(offset.left, offset.top);
        header.draw(canvas);
        canvas.restore();
    }
}
