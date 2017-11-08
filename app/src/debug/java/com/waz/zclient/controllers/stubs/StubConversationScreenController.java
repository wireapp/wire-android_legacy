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
package com.waz.zclient.controllers.stubs;

import android.view.View;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;

public class StubConversationScreenController implements IConversationScreenController {
    @Override
    public boolean isShowingParticipant() {
        return false;
    }

    @Override
    public void setShowDevicesTab(User user) {

    }

    @Override
    public boolean shouldShowDevicesTab() {
        return false;
    }

    @Override
    public void setParticipantHeaderHeight(int participantHeaderHeight) {

    }

    @Override
    public void addPeopleToConversation() {

    }

    @Override
    public void showConversationMenu(int requester, IConversation conversation, View anchorView) {

    }

    @Override
    public void resetToMessageStream() {

    }

    @Override
    public void showUser(User user) {

    }

    @Override
    public boolean isShowingUser() {
        return false;
    }

    @Override
    public void removeConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver) {

    }

    @Override
    public void editConversationName(boolean b) {

    }

    @Override
    public void showParticipants(View anchorView, boolean showDeviceTabIfSingle) {

    }

    @Override
    public boolean isConversationStreamUiInitialized() {
        return false;
    }

    @Override
    public void setMemberOfConversation(boolean isMemberOfConversation) {

    }

    @Override
    public void setPopoverLaunchedMode(DialogLaunchMode launchedMode) {

    }

    @Override
    public void addConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver) {

    }

    @Override
    public User getRequestedDeviceTabUser() {
        return null;
    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

    }

    @Override
    public DialogLaunchMode getPopoverLaunchMode() {
        return null;
    }

    @Override
    public void hideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange) {

    }

    @Override
    public void showLikesList(Message message) {

    }

    @Override
    public void hideOtrClient() {

    }

    @Override
    public void hideUser() {

    }

    @Override
    public void showOtrClient(OtrClient otrClient, User user) {

    }

    @Override
    public void setConversationStreamUiReady(boolean ready) {

    }

    @Override
    public void tearDown() {

    }

    @Override
    public void showCurrentOtrClient() {

    }

    @Override
    public void setSingleConversation(boolean isSingleConversation) {

    }

    @Override
    public boolean isSingleConversation() {
        return false;
    }
}
