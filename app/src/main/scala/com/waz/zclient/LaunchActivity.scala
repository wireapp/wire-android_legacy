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
package com.waz.zclient

import android.content.Intent
import com.waz.ZLog.ImplicitTag._
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.utils.{BackendPicker, Callback}


class LaunchActivity extends BaseActivity {
  override def getBaseTheme = R.style.Theme_Dark

  override def onBaseActivityStart() = {
    new BackendPicker(getApplicationContext).withBackend(this, new Callback[Void]() {
      override def callback(aVoid: Void) = {
        superOnBaseActivityStart()

        //TODO - could this be racing with setting the active account?
        inject[AccountsService].activeAccountId.head.map {
          case Some(_) => startMain()
          case _       => startSignUp()
        } (Threading.Ui)
      }
    })
  }

  //Can't call super from within anonymous class
  private def superOnBaseActivityStart() = super.onBaseActivityStart()

  override protected def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
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


