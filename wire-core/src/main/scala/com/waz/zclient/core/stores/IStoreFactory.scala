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
package com.waz.zclient.core.stores

import com.waz.zclient.core.stores.api.IZMessagingApiStore
import com.waz.zclient.core.stores.appentry.IAppEntryStore
import com.waz.zclient.core.stores.connect.IConnectStore
import com.waz.zclient.core.stores.conversation.IConversationStore
import com.waz.zclient.core.stores.draft.IDraftStore
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore
import com.waz.zclient.core.stores.network.INetworkStore
import com.waz.zclient.core.stores.participants.IParticipantsStore
import com.waz.zclient.core.stores.pickuser.IPickUserStore
import com.waz.zclient.core.stores.profile.IProfileStore
import com.waz.zclient.core.stores.singleparticipants.ISingleParticipantStore

trait IStoreFactory {
  def tearDown(): Unit

  def isTornDown: Boolean

  /* app entry*/
  def appEntryStore: IAppEntryStore

  /* managing the conversation list */
  def conversationStore: IConversationStore

  /* managing settings and properties of the user */
  def profileStore: IProfileStore

  /* managing the pick user view */
  def pickUserStore: IPickUserStore

  /* managing connecting & blocking to users */
  def connectStore: IConnectStore

  /* managing the participants view (old meta view) */
  def participantsStore: IParticipantsStore

  def singleParticipantStore: ISingleParticipantStore

  /* In App notification store (chathead, knocks) */
  def inAppNotificationStore: IInAppNotificationStore

  /* stores started messages */
  def draftStore: IDraftStore

  def zMessagingApiStore: IZMessagingApiStore

  def networkStore: INetworkStore

  def reset(): Unit
}
