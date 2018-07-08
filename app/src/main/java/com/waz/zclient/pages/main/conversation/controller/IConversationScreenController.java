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

public interface IConversationScreenController {

    void addConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    void removeConversationControllerObservers(ConversationScreenControllerObserver conversationScreenControllerObserver);

    boolean showUser(UserId userId);

    void hideUser();

    boolean isShowingUser();

    void tearDown();

    void setPopoverLaunchedMode(DialogLaunchMode launchedMode);

    void showConversationMenu(boolean inConvList, ConvId convId);

    DialogLaunchMode getPopoverLaunchMode();

    void hideOtrClient();
}
