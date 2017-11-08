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

public interface ConversationScreenControllerObserver {

    void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle);

    void onHideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange, boolean isSingleConversation);

    void onShowEditConversationName(boolean show);

    void onHeaderViewMeasured(int participantHeaderHeight);

    void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom);

    void onConversationLoaded();

    void onShowUser(User user);

    void onHideUser();

    void onAddPeopleToConversation();

    void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester, IConversation conversation, View anchorView);

    void onShowOtrClient(OtrClient otrClient, User user);

    void onShowCurrentOtrClient();

    void onHideOtrClient();

    void onShowLikesList(Message message);
}
