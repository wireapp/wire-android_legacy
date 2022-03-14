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

import android.app.AlertDialog
import android.content.{DialogInterface, Intent}
import androidx.appcompat.app.AppCompatActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountsService, BackendConfig}
import com.waz.threading.Threading
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.BackendController

class LaunchActivity extends AppCompatActivity with ActivityHelper with DerivedLogTag {

  private lazy val backendController = inject[BackendController]

  override def onStart() = {
    super.onStart()
    loadBackend()
  }

  private def loadBackend(): Unit = {
    val callback: BackendConfig => Unit = { be =>
      getApplication.asInstanceOf[WireApplication].ensureInitialized(be)
      inject[AccountsService].activeAccountId.head.map {
        case Some(_) => startMain()
        case _       => startSignUp()
      }(Threading.Ui)
    }

    if (backendController.shouldShowBackendSelector) showDialog(callback)
    else backendController.getStoredBackendConfig match {
      case Some(be) => callback(be)
      case None =>
        backendController.storeBackendConfig(Backend.ProdBackend)
        callback(Backend.ProdBackend)
    }
  }

  /// Presents a dialog to select backend.
  private def showDialog(callback: BackendConfig => Unit): Unit = {
    val environments = Backend.byName
    val items: Array[CharSequence] = environments.keys.toSeq.sorted.toArray

    val builder = new AlertDialog.Builder(this)
    builder.setTitle("Select Backend")

    builder.setItems(items, new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        val choice = items.apply(which).toString
        val config = environments.apply(choice)
        backendController.storeBackendConfig(config)
        callback(config)
      }
    })

    builder.setCancelable(false)
    if (!isFinishing)  builder.create().show()

    // QA needs to be able to switch backends via intents. Any changes to the backend
    // preference while the dialog is open will be treated as a user selection.
    backendController.onPreferenceSet(callback)
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
    startActivity(AppEntryActivity.newIntent(this))
    finish()
  }
}
