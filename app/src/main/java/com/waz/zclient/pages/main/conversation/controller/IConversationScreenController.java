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

import android.support.annotation.IntDef;
import android.view.View;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;

public interface IConversationScreenController {


    @IntDef({CONVERSATION_LIST_SWIPE,
             CONVERSATION_LIST_LONG_PRESS,
             CONVERSATION_DETAILS,
             USER_PROFILE_PARTICIPANTS,
             USER_PROFILE_SEARCH})
    @interface ConversationMenuRequester { }
    int CONVERSATION_LIST_SWIPE = 0;
    int CONVERSATION_LIST_LONG_PRESS = 1;
    int CONVERSATION_DETAILS = 2;
    int USER_PROFILE_PARTICIPANTS = 3;
    int USER_PROFILE_SEARCH = 4;

    void addConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    void removeConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    void showParticipants(View anchorView, boolean showDeviceTabIfSingle);

    void hideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange);

    void editConversationName(boolean b);

    void setShowDevicesTab(User user);

    boolean shouldShowDevicesTab();

    User getRequestedDeviceTabUser();

    boolean isShowingParticipant();

    void resetToMessageStream();

    void setParticipantHeaderHeight(int participantHeaderHeight);

    void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom);

    boolean isSingleConversation();

    void setSingleConversation(boolean isSingleConversation);

    void setMemberOfConversation(boolean isMemberOfConversation);

    void addPeopleToConversation();

    void showUser(User user);

    void hideUser();

    boolean isShowingUser();

    void tearDown();

    boolean isConversationStreamUiInitialized();

    void setConversationStreamUiReady(boolean ready);

    void setPopoverLaunchedMode(DialogLaunchMode launchedMode);

    void showConversationMenu(@ConversationMenuRequester int requester, IConversation conversation, View anchorView);

    DialogLaunchMode getPopoverLaunchMode();

    void showOtrClient(OtrClient otrClient, User user);

    void showCurrentOtrClient();

    void hideOtrClient();

    void showLikesList(Message message);
}
