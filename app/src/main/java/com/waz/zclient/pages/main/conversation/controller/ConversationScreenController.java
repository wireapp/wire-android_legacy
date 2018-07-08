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
package com.waz.zclient.pages.main.conversation.controller;

import com.waz.model.ConvId;
import com.waz.model.UserId;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;

import java.util.HashSet;
import java.util.Set;

public class ConversationScreenController implements IConversationScreenController {
    private Set<ConversationScreenControllerObserver> conversationScreenControllerObservers = new HashSet<>();

    private boolean isShowingUser;
    private DialogLaunchMode launchMode;

    @Override
    public void addConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver) {
        // Prevent concurrent modification (if this add was executed by one of current observers during notify* callback)
        Set<ConversationScreenControllerObserver> observers = new HashSet<>(conversationScreenControllerObservers);
        observers.add(conversationScreenControllerObserver);
        conversationScreenControllerObservers = observers;
    }

    @Override
    public void removeConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver) {
        // Prevent concurrent modification
        if (conversationScreenControllerObservers.contains(conversationScreenControllerObserver)) {
            Set<ConversationScreenControllerObserver> observers = new HashSet<>(conversationScreenControllerObservers);
            observers.remove(conversationScreenControllerObserver);
            conversationScreenControllerObservers = observers;
        }
    }

    @Override
    public boolean showUser(UserId userId) {
        if (userId == null || isShowingUser) {
            return false;
        }
        isShowingUser = true;
        return true;
    }

    @Override
    public void hideUser() {
        if (!isShowingUser) {
            return;
        }
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onHideUser();
        }
        isShowingUser = false;
        if (launchMode == DialogLaunchMode.AVATAR) {
            launchMode = null;
        }
    }

    @Override
    public boolean isShowingUser() {
        return isShowingUser;
    }

    @Override
    public void tearDown() {
        conversationScreenControllerObservers.clear();
        conversationScreenControllerObservers = null;
    }

    @Override
    public void setPopoverLaunchedMode(DialogLaunchMode launchedFrom) {
        this.launchMode = launchedFrom;
    }

    @Override
    public void showConversationMenu(boolean inConvList, ConvId convId) {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowConversationMenu(inConvList, convId);
        }
    }

    @Override
    public DialogLaunchMode getPopoverLaunchMode() {
        return launchMode;
    }

    @Override
    public void hideOtrClient() {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onHideOtrClient();
        }
    }

}
