/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.api.scala

import com.waz.zclient.core.stores.StoreFactory
import android.content.Context
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.core.api.scala._
import com.waz.zclient.core.stores.api.ZMessagingApiStore

class ScalaStoreFactory(context: Context, selectionController: => SelectionController) extends StoreFactory {

  override protected def createZMessagingApiStore = new ZMessagingApiStore(context)

  protected def createNetworkStore = new ScalaNetworkStore(zMessagingApiStore.getApi)

  protected def createAppEntryStore = new AppEntryStore(context, zMessagingApiStore.getApi)

  protected def createConversationStore = new ScalaConversationStore(zMessagingApiStore.getApi, selectionController)

  protected def createProfileStore = new ScalaProfileStore(zMessagingApiStore.getApi)

  protected def createPickUserStore = new ScalaPickUserStore(zMessagingApiStore.getApi)

  protected def createParticipantsStore = new ScalaParticipantsStore

  protected def createSingleParticipantStore = new ScalaSingleParticipantStore

  protected def createInAppNotificationStore = new ScalaInAppNotificationStore(zMessagingApiStore.getApi)

  def createConnectStore = new ScalaConnectStore(context, zMessagingApiStore.getApi)

  protected def createDraftStore = new ScalaDraftStore

  override def tearDown() = super.tearDown()
}
