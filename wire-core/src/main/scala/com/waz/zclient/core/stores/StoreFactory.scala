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

import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore
import com.waz.zclient.utils.Lazy

abstract class StoreFactory extends IStoreFactory {

  private val _inAppNotificationStore = lazyStore { createInAppNotificationStore() }

  private var tornDown = false

  protected def createInAppNotificationStore(): IInAppNotificationStore

  override def inAppNotificationStore = _inAppNotificationStore()

  override def reset(): Unit = {
    inAppNotificationStore.tearDown()
    tornDown = false
  }

  override def tearDown(): Unit = {
    reset()
    tornDown = true
  }

  override def isTornDown = tornDown

  private def verifyLifecycle() = if (isTornDown) throw new IllegalStateException("StoreFactory is already torn down")

  private def lazyStore[T <: IStore](create: => T) = Lazy[T]({ verifyLifecycle(); create }, { (t: T) => t.tearDown() })

}
