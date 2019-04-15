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
import android.widget.Toast
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountsService, BackendConfig}
import com.waz.threading.Threading
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.deeplinks.{DeepLink, DeepLinkParser}
import com.waz.zclient.utils.{BackendPicker, Callback}
import com.waz.zclient.log.LogUI._

class LaunchActivity extends AppCompatActivity with ActivityHelper with DerivedLogTag {

  override def onStart() = {
    super.onStart()

    /**
      * Not sure how to setup injection since this relies on GlobalModule, perhaps we should just
      * create an instance manually instead of using DI.
      */
    //inject[CustomBackendDeepLink].checkForCustomBackend(getIntent)
    //These logging lines never seem to get printed. I'm not sure if the below code is working or
    //not. The eas
    verbose(l"In LaunchActivity on start")
    Option(getIntent.getDataString) match {
      case Some(p) =>
          verbose(l"Got intent: $p")
          DeepLinkParser.parseLink(p) match {
            case Some((DeepLink.Access, token)) =>
              DeepLinkParser.parseToken(DeepLink.Access, token).foreach { t =>
                verbose(l"Got token: $token")
                Toast.makeText(getApplicationContext, s"Got token $t", Toast.LENGTH_LONG)
                //val builder = new AlertDialog.Builder(this)
                //builder.setTitle("Change backend?")
                //builder.setMessage(s"Are you sure you wish to load a config from $t")
                //builder.setCancelable(true)
                //val dialog = builder.create()
                //dialog.show()
              }
            case e => error(l"Ignoring deep link: $e")
          }
      case None => error(l"Got no intent!!!")
    }

    new BackendPicker(getApplicationContext).withBackend(this, new Callback[BackendConfig]() {
      override def callback(be: BackendConfig) = {
        getApplication.asInstanceOf[WireApplication].ensureInitialized(be)

        //TODO - could this be racing with setting the active account?
        inject[AccountsService].activeAccountId.head(LogTag("BackendPicker")).map {
          case Some(_) => startMain()
          case _       => startSignUp()
        } (Threading.Ui)
      }
    }, Backend.ProdBackend)
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


