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
package com.waz.zclient.pages.main.conversationpager.controller;


import android.view.View;

/**
 * Listener for monitoring events about sliding panes.
 */
public interface SlidingPaneObserver {
    /**
     * Called when a sliding pane's position changes.
     *
     * @param panel       The child view that was moved
     * @param slideOffset The new offset of this sliding pane within its range, from 0-1
     */
    void onPanelSlide(View panel, float slideOffset);

    /**
     * Called when a sliding pane becomes slid completely open. The pane may or may not
     * be interactive at this point depending on how much of the pane is visible.
     *
     * @param panel The child view that was slid to an open position, revealing other panes
     */
    void onPanelOpened(View panel);

    /**
     * Called when a sliding pane becomes slid completely closed. The pane is now guaranteed
     * to be interactive. It may now obscure other views in the layout.
     *
     * @param panel The child view that was slid to a closed position
     */
    void onPanelClosed(View panel);
}
