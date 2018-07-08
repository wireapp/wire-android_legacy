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
package com.waz.zclient.utils.keyboard;

import android.view.View;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ContextUtils;
import timber.log.Timber;

public class KeyboardVisibilityListener {
    private final View contentView;
    private final int statusAndNavigationBarHeight;
    private int keyboardHeight;

    public interface Callback {
        void onKeyboardChanged(boolean keyboardIsVisible, int keyboardHeight);
        void onKeyboardHeightChanged(int keyboardHeight);
    }
    private Callback callback;

    public KeyboardVisibilityListener(View contentView) {
        this.contentView = contentView;
        if (contentView == null) {
            this.statusAndNavigationBarHeight = 0;
            return;
        }
        this.statusAndNavigationBarHeight = ContextUtils.getNavigationBarHeight(contentView.getContext()) + ContextUtils.getStatusBarHeight(contentView.getContext());
    }

    public void setCallback(Callback keyboardCallback) {
        this.callback = keyboardCallback;
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public synchronized void onLayoutChange() {
        int newKeyboardHeight = Math.max(0, KeyboardUtils.getKeyboardHeight(contentView) - statusAndNavigationBarHeight);

        if (newKeyboardHeight != keyboardHeight) {
            Timber.i("keyboard height changes from %s to %s", keyboardHeight, newKeyboardHeight);
            boolean visibilityChanged = keyboardHeight == 0 || newKeyboardHeight == 0;
            keyboardHeight = newKeyboardHeight;

            if (callback != null) {
                callback.onKeyboardHeightChanged(newKeyboardHeight);
                if (visibilityChanged) {
                    callback.onKeyboardChanged(newKeyboardHeight > 0, newKeyboardHeight);
                }
            }
        }

    };
}
