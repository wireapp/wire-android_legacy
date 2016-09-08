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
package com.waz.zclient.core.stores.stub;

import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.Invitations;
import com.waz.api.User;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;

public class StubConnectStore implements IConnectStore {
  @Override
  public void loadUser(String userId, IConnectStore.UserRequester userRequester) {
    ;
  }

  @Override
  public void loadCommonConnections(CommonConnections commonConnections) {
    ;
  }

  @Override
  public void blockUser(User user) {
    ;
  }

  @Override
  public void requestInviteUri(Invitations.InvitationUriCallback callback) {
    ;
  }

  @Override
  public IConversation unblockUser(User user) {
    return null;
  }

  @Override
  public void tearDown() {
    ;
  }

  @Override
  public void addConnectRequestObserver(ConnectStoreObserver connectStoreObserver) {
    ;
  }

  @Override
  public void removeConnectRequestObserver(ConnectStoreObserver connectStoreObserver) {
    ;
  }

  @Override
  public void requestConnection(String token) {
    ;
  }

  @Override
  public IConversation connectToNewUser(User user, String firstMessage) {
    return null;
  }
}
