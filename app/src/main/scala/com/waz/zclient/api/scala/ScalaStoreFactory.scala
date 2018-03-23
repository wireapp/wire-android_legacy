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
package com.waz.zclient.api.scala

import android.content.{ContentResolver, Context}
import android.net.Uri
import com.waz.api.impl.ErrorsList
import com.waz.service.ZMessaging
import com.waz.zclient.core.api.scala._
import com.waz.zclient.core.stores.StoreFactory

class ScalaStoreFactory(context: Context) extends StoreFactory {

  protected def createInAppNotificationStore() = new ScalaInAppNotificationStore(ZMessaging.currentUi.cached(Uris.ErrorsUri, new ErrorsList()(ZMessaging.currentUi)))

  override def tearDown() = super.tearDown()
}

object Uris {
  val ErrorsUri = Uri.parse(s"${ContentResolver.SCHEME_CONTENT}://com.waz/errors")
}
