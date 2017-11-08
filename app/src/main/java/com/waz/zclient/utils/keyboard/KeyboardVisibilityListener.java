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
package com.waz.zclient.utils.keyboard;

import android.graphics.Rect;
import android.os.Handler;
import android.view.View;
import com.waz.zclient.utils.ViewUtils;

public class KeyboardVisibilityListener {
    private final View contentView;
    private final int statusAndNavigationBarHeight;
    private int lastKeyboardHeight;
    private int keyboardHeight;

    public interface Callback {
        void onKeyboardChanged(boolean keyboardIsVisible, int keyboardHeight);
        void onKeyboardHeightChanged(int keyboardHeight);
    }
    private Callback callback;

    private Handler keyboardHeightHandler;
    private Runnable keyboardHeightRunnable = new Runnable() {
        public void run() {
        if (callback != null) {
            callback.onKeyboardHeightChanged(keyboardHeight);
        }
        }
    };

    public KeyboardVisibilityListener(View contentView) {
        this.contentView = contentView;
        this.keyboardHeightHandler = new Handler();
        if (contentView == null) {
            this.statusAndNavigationBarHeight = 0;
            return;
        }
        this.statusAndNavigationBarHeight = ViewUtils.getNavigationBarHeight(contentView.getContext()) + ViewUtils.getStatusBarHeight(contentView.getContext());
    }

    public void setCallback(Callback keyboardCallback) {
        this.callback = keyboardCallback;
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public void onLayoutChange() {
        Rect r = new Rect();
        contentView.getWindowVisibleDisplayFrame(r);
        int screenHeight = contentView.getRootView().getHeight();
        keyboardHeight = screenHeight - r.bottom - statusAndNavigationBarHeight;

        if (keyboardHeight == lastKeyboardHeight) {
            return;
        }

        // Use delay to filter out intermediate height values
        keyboardHeightHandler.removeCallbacks(keyboardHeightRunnable);
        keyboardHeightHandler.postDelayed(keyboardHeightRunnable, 200);

        if (lastKeyboardHeight > 0 && keyboardHeight > 0) {
            lastKeyboardHeight = keyboardHeight;
            return;
        }

        lastKeyboardHeight = keyboardHeight;

        if (callback != null) {
            callback.onKeyboardChanged(keyboardHeight > 0, keyboardHeight);
        }
    }
}
