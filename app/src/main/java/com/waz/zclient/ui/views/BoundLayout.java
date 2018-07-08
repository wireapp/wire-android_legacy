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
package com.waz.zclient.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;

public class BoundLayout extends FrameLayout {

    private static final int NO_WIDTH = -1;

    private int maxWidth;
    private int maxHeight;
    private int minWidth;
    private int minHeight;

    public BoundLayout(Context context) {
        this(context, null);
    }

    public BoundLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoundLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attributeSet) {
        final TypedArray attrs = getContext().obtainStyledAttributes(attributeSet, R.styleable.BoundLayout);
        try {
            maxWidth = attrs.getDimensionPixelSize(R.styleable.BoundLayout_maximumWidth, NO_WIDTH);
            maxHeight = attrs.getDimensionPixelSize(R.styleable.BoundLayout_maximumHeight, NO_WIDTH);
            minWidth = attrs.getDimensionPixelSize(R.styleable.BoundLayout_minimumWidth, NO_WIDTH);
            minHeight = attrs.getDimensionPixelSize(R.styleable.BoundLayout_minimumHeight, NO_WIDTH);
        } finally {
            attrs.recycle();
        }
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        requestLayout();
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        requestLayout();
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        requestLayout();
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = 0;
        int measuredHeight = 0;

        final boolean wrapContentWidth = getLayoutParams().width == LayoutParams.WRAP_CONTENT;
        final boolean wrapContentHeight = getLayoutParams().height == LayoutParams.WRAP_CONTENT;

        if (!wrapContentWidth) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        }

        if (!wrapContentHeight) {
            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        }

        if (wrapContentWidth || wrapContentHeight) {
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);

                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();

                if (wrapContentWidth) {
                    final int margins = params.leftMargin + params.rightMargin;
                    measuredWidth = Math.max(measuredWidth, child.getMeasuredWidth() + margins);
                }

                if (wrapContentHeight) {
                    final int margins = params.topMargin + params.bottomMargin;
                    measuredHeight = Math.max(measuredHeight, child.getMeasuredHeight() + margins);
                }
            }

        }

        final int width;
        if (maxWidth != NO_WIDTH && maxWidth < measuredWidth) {
            width = maxWidth;
        } else if (minWidth != NO_WIDTH && minWidth > measuredWidth) {
            width = minWidth;
        } else {
            width = measuredWidth;
        }

        final int height;
        if (maxHeight != NO_WIDTH && maxHeight < measuredHeight) {
            height = maxHeight;
        } else if (minHeight != NO_WIDTH && minHeight > measuredHeight) {
            height = minHeight;
        } else {
            height = measuredHeight;
        }

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthMeasureSpec));
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.getMode(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
