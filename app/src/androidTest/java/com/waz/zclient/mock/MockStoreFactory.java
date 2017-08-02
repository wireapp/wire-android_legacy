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
package com.waz.zclient.mock;

import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.core.stores.api.IZMessagingApiStore;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.draft.IDraftStore;
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore;
import com.waz.zclient.core.stores.network.INetworkStore;
import com.waz.zclient.core.stores.participants.IParticipantsStore;
import com.waz.zclient.core.stores.pickuser.IPickUserStore;
import com.waz.zclient.core.stores.profile.IProfileStore;
import com.waz.zclient.core.stores.singleparticipants.ISingleParticipantStore;
import com.waz.zclient.core.stores.stub.StubAppEntryStore;
import com.waz.zclient.core.stores.stub.StubConnectStore;
import com.waz.zclient.core.stores.stub.StubDraftStore;
import com.waz.zclient.core.stores.stub.StubInAppNotificationStore;
import com.waz.zclient.core.stores.stub.StubNetworkStore;
import com.waz.zclient.core.stores.stub.StubParticipantsStore;
import com.waz.zclient.core.stores.stub.StubPickUserStore;
import com.waz.zclient.core.stores.stub.StubProfileStore;
import com.waz.zclient.core.stores.stub.StubSingleParticipantStore;
import com.waz.zclient.core.stores.stub.StubZMessagingApiStore;

import static org.mockito.Mockito.spy;

public class MockStoreFactory implements IStoreFactory {
  protected IZMessagingApiStore zMessagingApiStore = spy(StubZMessagingApiStore.class);

  protected IAppEntryStore appEntryStore = spy(StubAppEntryStore.class);

  protected IConnectStore connectStore = spy(StubConnectStore.class);

  protected IDraftStore draftStore = spy(StubDraftStore.class);

  protected IInAppNotificationStore inAppNotificationStore = spy(StubInAppNotificationStore.class);

  protected INetworkStore networkStore = spy(StubNetworkStore.class);

  protected IParticipantsStore participantsStore = spy(StubParticipantsStore.class);

  protected IPickUserStore pickUserStore = spy(StubPickUserStore.class);

  protected IProfileStore profileStore = spy(StubProfileStore.class);

  protected ISingleParticipantStore singleParticipantStore = spy(StubSingleParticipantStore.class);

  @Override
  public IProfileStore profileStore() {
    return profileStore;
  }

  @Override
  public boolean isTornDown() {
    return false;
  }

  @Override
  public IDraftStore draftStore() {
    return draftStore;
  }

  @Override
  public IZMessagingApiStore zMessagingApiStore() {
    return zMessagingApiStore;
  }

  @Override
  public ISingleParticipantStore singleParticipantStore() {
    return singleParticipantStore;
  }

  @Override
  public IAppEntryStore appEntryStore() {
    return appEntryStore;
  }

  @Override
  public INetworkStore networkStore() {
    return networkStore;
  }

  @Override
  public IInAppNotificationStore inAppNotificationStore() {
    return inAppNotificationStore;
  }

  @Override
  public IParticipantsStore participantsStore() {
    return participantsStore;
  }

  @Override
  public void reset() {
  }

  @Override
  public IPickUserStore pickUserStore() {
    return pickUserStore;
  }

  @Override
  public IConversationStore conversationStore() {
    return null;
  }

  @Override
  public void tearDown() {
  }

  @Override
  public IConnectStore connectStore() {
    return connectStore;
  }
}
