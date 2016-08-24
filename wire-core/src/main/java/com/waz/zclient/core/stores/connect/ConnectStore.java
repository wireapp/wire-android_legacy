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
import com.waz.api.User;

import java.util.HashSet;
import java.util.Set;

public abstract class ConnectStore implements IConnectStore {

    protected Set<ConnectStoreObserver> connectStoreObservers = new HashSet<>();

    @Override
    public void addConnectRequestObserver(ConnectStoreObserver connectStoreObserver) {
        connectStoreObservers.add(connectStoreObserver);
    }

    @Override
    public void removeConnectRequestObserver(ConnectStoreObserver connectStoreObserver) {
        connectStoreObservers.remove(connectStoreObserver);
    }

    protected void notifyConnectUserUpdated(User user, UserRequester userRequester) {
        for (ConnectStoreObserver connectStoreObserver : connectStoreObservers) {
            connectStoreObserver.onConnectUserUpdated(user, userRequester);
        }
    }

    protected void notifyCommonConnectionsUpdated(CommonConnections commonConnections) {
        for (ConnectStoreObserver connectStoreObserver : connectStoreObservers) {
            connectStoreObserver.onCommonConnectionsUpdated(commonConnections);
        }
    }

    protected void notifyInviteRequestSent(IConversation conversation) {
        for (ConnectStoreObserver connectStoreObserver : connectStoreObservers) {
            connectStoreObserver.onInviteRequestSent(conversation);
        }
    }

    @Override
    public void blockUser(User user) {
        if (user != null) {
            user.block();
        }
    }

    @Override
    public IConversation unblockUser(User user) {
        if (user != null) {
            return user.unblock();
        }
        return null;
    }
}
