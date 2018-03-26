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
package com.waz.zclient.preferences.pages

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.v4.app.ShareCompat
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.MenuRowButton
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, ContextUtils}
import com.waz.zclient.{R, SpinnerController, ViewHelper}

import scala.concurrent.Future

class BackupExportView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.backup_export_layout)

  val zms                = inject[Signal[ZMessaging]]
  val navigator          = inject[BackStackNavigator]
  val spinnerController  = inject[SpinnerController]

  private lazy val backupButton = findById[MenuRowButton](R.id.backup_button)

  backupButton.setOnClickProcess(backupData, showSpinner = false)

  private def backupData: Future[Unit] = {
    spinnerController.showDimmedSpinner(show = true, ContextUtils.getString(R.string.back_up_progress))
    import Threading.Implicits.Ui

    (for {
      z                <- zms.head
      Some(accManager) <- z.accounts.activeAccountManager.head
      res              <- accManager.exportDatabase()
    } yield res).map { file =>
      val intent = ShareCompat.IntentBuilder.from(context.asInstanceOf[Activity]).setType("text/plain").setText("backup").getIntent
      context.startActivity(intent)
      spinnerController.showDimmedSpinner(show = false)
    }
  }
}

object BackupExportView {

}

case class BackupExportKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_backup_screen_title

  override def layoutId = R.layout.preferences_backup_export

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
