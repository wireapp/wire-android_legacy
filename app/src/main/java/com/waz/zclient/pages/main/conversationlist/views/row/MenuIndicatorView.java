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
package com.waz.zclient.pages.main.conversationlist.views.row;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

public class MenuIndicatorView extends FrameLayout {

    private View firstDotView;
    private View secondDotView;
    private int defaultTranslateX;
    private int firstDotAnimationOffsetX;
    private int secondDotAnimationOffsetX;

    // clips the canvas horizontally at this position
    private int clipX;

    // max offset needed to calculate the appropriate alpha values
    private float maxOffset;

    /**
     * the max offset is set by the SwipeListView. It needs to calculate its own width first.
     */
    public void setMaxOffset(float maxOffset) {
        this.maxOffset = maxOffset;
    }

    /**
     * Sets the clip value and calculates the corresponding alphas.
     */
    public void setClipX(int clipX) {
        this.clipX = 10 * clipX;

        if (maxOffset > 0) {
            float ratio = clipX / maxOffset;
            if (ratio > 1) {
                ratio = 1;
            }
            ratio = 1 - ratio;

            int deltaX = (int) (ratio * defaultTranslateX);
            setTranslationX(-deltaX);
            firstDotView.setTranslationX(-(int) (ratio * firstDotAnimationOffsetX));
            secondDotView.setTranslationX(-(int) (ratio * secondDotAnimationOffsetX));
        }

        invalidate();
    }


    public MenuIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MenuIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MenuIndicatorView(Context context) {
        super(context);
        init();
    }

    /**
     * CTOR - Initializes the views
     */
    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.conv_list_item_behind, this, true);
        firstDotView = ViewUtils.getView(this, R.id.v__row_conversation__menu_indicator__first_dot);
        secondDotView = ViewUtils.getView(this, R.id.v__row_conversation__menu_indicator__second_dot);

        int totalWidth = getResources().getDimensionPixelSize(R.dimen.conversation_list__left_icon_width);
        int distanceDotsToContainerEdge = (totalWidth - 3 * getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__dot__radius) - 2 *  getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__dot__horizontal_margin)) / 2;
        defaultTranslateX = totalWidth - distanceDotsToContainerEdge;

        firstDotAnimationOffsetX = getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__dot__first_animation_offset_x);
        secondDotAnimationOffsetX = getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__dot__second_animation_offset_x);
        firstDotView.setTranslationX(-firstDotAnimationOffsetX);
        secondDotView.setTranslationX(-secondDotAnimationOffsetX);
    }

    /**
     * This is where the magic happens. the canvas is clipped at clipX.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.clipRect(new Rect(0, 0, clipX, canvas.getHeight()));
        super.dispatchDraw(canvas);
    }
}
