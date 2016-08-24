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
package com.waz.zclient.core.stores.connect;

import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.Invitations;
import com.waz.api.User;
import com.waz.zclient.core.stores.IStore;

public interface IConnectStore extends IStore {

    enum UserRequester {
        SEARCH, CONVERSATION, PARTICIPANTS, INVITE, POPOVER, CALL
    }

    // Displaying a connect request
    void loadUser(String userId, UserRequester userRequester);

    void loadCommonConnections(CommonConnections commonConnections);

    void addConnectRequestObserver(ConnectStoreObserver connectStoreObserver);

    void removeConnectRequestObserver(ConnectStoreObserver connectStoreObserver);

    // Connect actions with another user
    IConversation connectToNewUser(User user, String firstMessage);

    void blockUser(User user);

    IConversation unblockUser(User user);

    void requestInviteUri(Invitations.InvitationUriCallback callback);

    void requestConnection(String token);

}
