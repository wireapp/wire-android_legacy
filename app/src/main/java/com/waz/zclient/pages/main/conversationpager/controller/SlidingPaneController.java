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

import java.util.HashSet;
import java.util.Set;

public class SlidingPaneController implements ISlidingPaneController {
    private Set<SlidingPaneObserver> slideListeners = new HashSet<>();

    @Override
    public void addObserver(SlidingPaneObserver slideListener) {
        slideListeners.add(slideListener);
    }

    @Override
    public void removeObserver(SlidingPaneObserver slideListener) {
        slideListeners.remove(slideListener);
    }

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
        for (SlidingPaneObserver slideListener : slideListeners) {
            slideListener.onPanelSlide(panel, slideOffset);
        }
    }

    @Override
    public void onPanelOpened(View panel) {
        for (SlidingPaneObserver slideListener : slideListeners) {
            slideListener.onPanelOpened(panel);
        }
    }

    @Override
    public void onPanelClosed(View panel) {
        for (SlidingPaneObserver slideListener : slideListeners) {
            slideListener.onPanelClosed(panel);
        }
    }

    @Override
    public void tearDown() {
        slideListeners.clear();
        slideListeners = null;
    }
}
