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
package com.waz.zclient.pages.main.conversation.controller;

import android.view.View;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;

import java.util.HashSet;
import java.util.Set;

public class ConversationScreenController implements IConversationScreenController {
    private Set<ConversationScreenControllerObserver> conversationScreenControllerObservers = new HashSet<>();

    private boolean isShowingParticipant;
    private boolean isSingleConversation;
    private boolean isMemberOfConversation;
    private boolean isShowingUser;
    private boolean conversationStreamUiReady;
    private DialogLaunchMode launchMode;
    private User showDevicesTabForUser;

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
    public void showParticipants(View anchorView, boolean showDeviceTabIfSingle) {
        isShowingParticipant = true;
        for (ConversationScreenControllerObserver conversationScreenControllerObserver : conversationScreenControllerObservers) {
            conversationScreenControllerObserver.onShowParticipants(anchorView, isSingleConversation, isMemberOfConversation, showDeviceTabIfSingle);
        }
    }

    @Override
    public void hideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange) {
        if (!isShowingParticipant &&
            launchMode == null) {
            return;
        }
        for (ConversationScreenControllerObserver conversationScreenControllerObserver : conversationScreenControllerObservers) {
            conversationScreenControllerObserver.onHideParticipants(backOrButtonPressed,
                                                                    hideByConversationChange,
                                                                    isSingleConversation);
        }
        resetToMessageStream();
    }

    @Override
    public void editConversationName(boolean edit) {
        for (ConversationScreenControllerObserver conversationManagerScreenControllerObserver : conversationScreenControllerObservers) {
            conversationManagerScreenControllerObserver.onShowEditConversationName(edit);
        }
    }

    @Override
    public void setShowDevicesTab(User user) {
        this.showDevicesTabForUser = user;
    }

    @Override
    public boolean shouldShowDevicesTab() {
        return showDevicesTabForUser != null;
    }

    @Override
    public User getRequestedDeviceTabUser() {
        return showDevicesTabForUser;
    }

    @Override
    public boolean isShowingParticipant() {
        return isShowingParticipant;
    }

    @Override
    public void resetToMessageStream() {
        isShowingParticipant = false;
        isShowingUser = false;
        showDevicesTabForUser = null;
        launchMode = null;
    }

    @Override
    public void setParticipantHeaderHeight(int participantHeaderHeight) {
        for (ConversationScreenControllerObserver conversationScreenControllerObserver : conversationScreenControllerObservers) {
            conversationScreenControllerObserver.onHeaderViewMeasured(participantHeaderHeight);
        }
    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {
        for (ConversationScreenControllerObserver conversationScreenControllerObserver : conversationScreenControllerObservers) {
            conversationScreenControllerObserver.onScrollParticipantsList(verticalOffset, scrolledToBottom);
        }
    }

    @Override
    public boolean isSingleConversation() {
        return isSingleConversation;
    }

    @Override
    public void setSingleConversation(boolean isSingleConversation) {
        this.isSingleConversation = isSingleConversation;
    }

    @Override
    public void setMemberOfConversation(boolean isMemberOfConversation) {
        this.isMemberOfConversation = isMemberOfConversation;
    }

    @Override
    public void addPeopleToConversation() {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onAddPeopleToConversation();
        }
    }

    @Override
    public void showUser(User user) {
        if (user == null || isShowingUser) {
            return;
        }
        isShowingUser = true;
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowUser(user);
        }
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
    public boolean isConversationStreamUiInitialized() {
        return conversationStreamUiReady;
    }

    @Override
    public void setConversationStreamUiReady(boolean conversationStreamUiReady) {
        if (this.conversationStreamUiReady == conversationStreamUiReady) {
            return;
        }
        this.conversationStreamUiReady = conversationStreamUiReady;
        if (!conversationStreamUiReady) {
            return;
        }
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onConversationLoaded();
        }
    }

    @Override
    public void setPopoverLaunchedMode(DialogLaunchMode launchedFrom) {
        this.launchMode = launchedFrom;
    }

    @Override
    public void showConversationMenu(@ConversationMenuRequester int requester, IConversation conversation, View anchorView) {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowConversationMenu(requester, conversation, anchorView);
        }
    }

    @Override
    public DialogLaunchMode getPopoverLaunchMode() {
        return launchMode;
    }

    @Override
    public void showOtrClient(OtrClient otrClient, User user) {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowOtrClient(otrClient, user);
        }
    }

    @Override
    public void showCurrentOtrClient() {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowCurrentOtrClient();
        }
    }

    @Override
    public void hideOtrClient() {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onHideOtrClient();
        }
    }

    @Override
    public void showLikesList(Message message) {
        for (ConversationScreenControllerObserver observer : conversationScreenControllerObservers) {
            observer.onShowLikesList(message);
        }
    }
}
