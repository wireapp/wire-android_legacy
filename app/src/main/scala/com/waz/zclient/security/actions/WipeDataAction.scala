/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.security.actions

import android.content.Context
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Background
import com.waz.zclient.WireApplication
import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future

/**
  * Wipe the local data (all DBs, crypto material, caches) of all accounts.
  */
class WipeDataAction()(implicit context: Context) extends SecurityChecklist.Action {

  override def execute(): Future[Unit] =
    ZMessaging.currentAccounts.wipeDataForAllAccounts()
      .map { _ =>
        WireApplication.clearOldVideoFiles(context)
        context.getCacheDir.delete()
      }
}
