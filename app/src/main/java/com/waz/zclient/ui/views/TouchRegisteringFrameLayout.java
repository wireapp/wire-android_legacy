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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class TouchRegisteringFrameLayout extends FrameLayout {

    private TouchCallback touchCallback;

    public TouchRegisteringFrameLayout(Context context) {
        super(context);
    }

    public TouchRegisteringFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchRegisteringFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTouchCallback(TouchCallback touchCallback) {
        this.touchCallback = touchCallback;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (touchCallback != null) {
            touchCallback.onInterceptTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    public interface TouchCallback {
        void onInterceptTouchEvent(MotionEvent event);
    }
}
