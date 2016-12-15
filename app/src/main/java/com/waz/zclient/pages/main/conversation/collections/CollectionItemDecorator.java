/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
import android.graphics.Color;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.waz.zclient.conversation.CollectionAdapter;


public class CollectionItemDecorator extends RecyclerView.ItemDecoration {

    private CollectionAdapter adapter;
    private int spanCount;

    public CollectionItemDecorator(CollectionAdapter adapter, int spanCount) {
        this.adapter = adapter;
        this.spanCount = spanCount;
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        super.onDrawOver(c, parent, state);
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
                View header = getHeaderView(parent, position);
                int translationX = parent.getLeft();
                int translationY = Math.max(itemView.getTop() - header.getHeight(), 0);
                tempRect.set(translationX, translationY, translationX + header.getWidth(),
                             translationY + header.getHeight());
                if(tempRect.bottom > highestTop) {
                    tempRect.offset(0, highestTop - tempRect.bottom);
                }
                drawHeader(c, header, tempRect);
                highestTop = tempRect.top;
            }
        }
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
            View header = getHeaderView(parent, itemPosition);
            outRect.top = header.getHeight();
        }
    }

    private View getHeaderView(RecyclerView parent, int position) {
        // TODO add real header (from adapter?)
        TextView header = new TextView(parent.getContext());
        header.setText("header with id " + adapter.getHeaderId(position));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        header.setTextColor(Color.RED);

        if (header.getLayoutParams() == null) {
            header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.EXACTLY);
        int childWidth = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft() + parent.getPaddingRight(), header.getLayoutParams().width);
        int childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop() + parent.getPaddingBottom(), header.getLayoutParams().height);
        header.measure(childWidth, childHeight);
        header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());

        return header;
    }

    private boolean isUnderHeader(int itemPosition) {
        return isUnderHeader(itemPosition, spanCount);
    }

    private boolean isUnderHeader(int itemPosition, int spanCount) {
        if (itemPosition == 0) {
            return true;
        }
        int headerId = adapter.getHeaderId(itemPosition);
        for (int i = 1; i < spanCount + 1; i++) {
            long previousHeaderId = -1;
            int previousItemPosition = itemPosition - i;
            if (previousItemPosition >= 0 && previousItemPosition < adapter.getItemCount()) {
                previousHeaderId = adapter.getHeaderId(previousItemPosition);
            }
            if (headerId != previousHeaderId) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstUnderHeader(int position) {
        return position == 0 || adapter.getHeaderId(position) != adapter.getHeaderId(position - 1);
    }

    private void drawHeader(Canvas canvas, View header, Rect offset) {
        canvas.save();
        canvas.translate(offset.left, offset.top);
        header.draw(canvas);
        canvas.restore();
    }
}
