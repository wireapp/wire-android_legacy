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

package com.waz.zclient

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendSelector

class LaunchActivity extends AppCompatActivity with ActivityHelper with DerivedLogTag {

  override def onStart() = {
    super.onStart()

    new BackendSelector()(this).selectBackend { be =>
      getApplication.asInstanceOf[WireApplication].ensureInitialized(be)
      inject[AccountsService].activeAccountId.head(LogTag("BackendSelector")).map {
        case Some(_) => startMain()
        case _ => startSignUp()
      }(Threading.Ui)
    }
  }

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    verbose(l"Setting intent")
    setIntent(intent)
  }

  // Navigation //////////////////////////////////////////////////
  private def startMain() = {
    startActivity(new Intent(this, classOf[MainActivity]))
    finish()
  }

  private def startSignUp() = {
    startActivity(new Intent(this, classOf[AppEntryActivity]))
    finish()
  }
}

object LaunchActivity {
  val Tag = classOf[LaunchActivity].getName
}


