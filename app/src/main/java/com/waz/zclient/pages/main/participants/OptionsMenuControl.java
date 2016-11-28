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
package com.waz.zclient.pages.main.participants;

import com.waz.api.IConversation;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.ui.theme.OptionsTheme;

public class OptionsMenuControl {

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void open() {
        if (callback != null) {
            callback.onOpenRequest();
        }
    }

    public boolean close() {
        if (callback != null) {
            return callback.onCloseRequest();
        }
        return false;
    }

    public void createMenu(IConversation conversation, @IConversationScreenController.ConversationMenuRequester int requester, OptionsTheme optionsTheme) {
        if (callback != null) {
            callback.onCreateMenu(conversation, requester, optionsTheme);
        }
    }

    public interface Callback {
        void onOpenRequest();

        boolean onCloseRequest();

        void onCreateMenu(IConversation conversation, @IConversationScreenController.ConversationMenuRequester int requester, OptionsTheme optionsTheme);
    }
}
