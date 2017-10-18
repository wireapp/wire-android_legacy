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
package com.waz.zclient

import android.content.Intent
import com.waz.api.ClientRegistrationState.PASSWORD_MISSING
import com.waz.api.{InitListener, Self}
import com.waz.zclient.Intents._
import com.waz.zclient.utils.{BackendPicker, Callback}

class LaunchActivity extends BaseActivity with InitListener {

  override def getBaseTheme: Int = R.style.Theme_Dark

  override def onBaseActivityStart() = {
    persistInviteToken()
    new BackendPicker(getApplicationContext).withBackend(this, new Callback[Void]() {
      override def callback(t: Void) = {
        LaunchActivity.super.onBaseActivityStart()
        getStoreFactory.zMessagingApiStore.getApi.onInit(LaunchActivity.this)
      }
    } )
  }

  protected override def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    setIntent(intent)
    persistInviteToken()
  }

  private def persistInviteToken(): Unit =
    getInviteToken(Option(getIntent)).foreach { token =>
      getControllerFactory
        .getUserPreferencesController
        .setGenericInvitationToken(token)
    }

  override def onInitialized(self: Self): Unit =
    if (self.isLoggedIn && self.getClientRegistrationState != PASSWORD_MISSING) {
      startMain()
    } else {
      startSignUp()
    }

  private def startMain(): Unit = {
    startActivity(new Intent(this, classOf[MainActivity]))
    finish()
  }

  private def startSignUp(): Unit = {
    startActivity(new Intent(this, classOf[AppEntryActivity]))
    finish()
  }
}
