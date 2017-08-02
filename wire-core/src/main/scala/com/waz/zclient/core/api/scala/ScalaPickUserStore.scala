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
package com.waz.zclient.core.api.scala

import com.waz.api.{User, ZMessagingApi}
import com.waz.zclient.core.stores.pickuser.IPickUserStore

class ScalaPickUserStore(zMessagingApi: ZMessagingApi) extends IPickUserStore {
  override def tearDown(): Unit = {}

  override def getUser(userId: String): User = zMessagingApi.getUser(userId)
}

object ScalaPickUserStore {
  val TAG = classOf[ScalaPickUserStore].getName
}
