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
package com.waz.zclient.core.stores

import com.waz.zclient.core.stores.api.IZMessagingApiStore
import com.waz.zclient.core.stores.conversation.IConversationStore
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore
import com.waz.zclient.core.stores.network.INetworkStore
import com.waz.zclient.core.stores.pickuser.IPickUserStore
import com.waz.zclient.core.stores.profile.IProfileStore
import com.waz.zclient.utils.Lazy

abstract class StoreFactory extends IStoreFactory {

  /*
    Lazy loaded stores
  */
  private val _conversationStore = lazyStore { createConversationStore() }
  private val _pickUserStore = lazyStore { createPickUserStore() }
  private val _profileStore = lazyStore { createProfileStore() }
  private val _inAppNotificationStore = lazyStore { createInAppNotificationStore() }
  private val _zMessagingApiStore = lazyStore { createZMessagingApiStore() }
  private val _networkStore = lazyStore { createNetworkStore() }

  private var tornDown = false

  protected def createZMessagingApiStore(): IZMessagingApiStore
  protected def createConversationStore(): IConversationStore
  protected def createProfileStore(): IProfileStore
  protected def createPickUserStore(): IPickUserStore
  protected def createInAppNotificationStore(): IInAppNotificationStore
  protected def createNetworkStore(): INetworkStore

  override def zMessagingApiStore = _zMessagingApiStore()
  override def conversationStore = _conversationStore()
  override def profileStore = _profileStore()
  override def pickUserStore = _pickUserStore()
  override def inAppNotificationStore = _inAppNotificationStore()
  override def networkStore = _networkStore()

  override def reset(): Unit = {
    conversationStore.tearDown()
    pickUserStore.tearDown()
    profileStore.tearDown()
    inAppNotificationStore.tearDown()
    networkStore.tearDown()

    tornDown = false
  }

  override def tearDown(): Unit = {
    reset()

    zMessagingApiStore.tearDown()
    tornDown = true
  }

  override def isTornDown = tornDown

  private def verifyLifecycle() = if (isTornDown) throw new IllegalStateException("StoreFactory is already torn down")

  private def lazyStore[T <: IStore](create: => T) = Lazy[T]({ verifyLifecycle(); create }, { (t: T) => t.tearDown() })

}
