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

import java.io.File

import android.app.{Activity, FragmentTransaction}
import android.content.Context
import android.os.Bundle
import androidx.core.app.ShareCompat
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.{AccountManager, UiLifeCycle, UserService}
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.MenuRowButton
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog
import com.waz.zclient.utils.{BackStackKey, ContextUtils, ExternalFileSharing, ViewUtils}
import com.waz.zclient._
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog.SetPasswordMode

import scala.concurrent.Future

class BackupExportView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.backup_export_layout)

  private val spinnerController = inject[SpinnerController]
  private val lifecycle         = inject[UiLifeCycle]
  private val sharing           = inject[ExternalFileSharing]
  private val backupButton      = findById[MenuRowButton](R.id.backup_button)

  backupButton.setOnClickProcess(requestPassword())

  def requestPassword(): Future[Unit] = {
    val fragment = returning(BackupPasswordDialog.newInstance(SetPasswordMode)) {
      _.onPasswordEntered(backupData)
    }

    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(fragment, BackupPasswordDialog.FragmentTag)
      .addToBackStack(BackupPasswordDialog.FragmentTag)
      .commit
    Future.successful(())
  }

  private def backupData(password: Password) = {
    import com.waz.zclient.utils.ScalaToKotlin._
    import Threading.Implicits.Ui

    spinnerController.showDimmedSpinner(show = true, ContextUtils.getString(R.string.back_up_progress))

    for {
      users          <- inject[Signal[UserService]].head
      self           <- users.selfUser.head
      userHandle     =  self.handle.fold("")(_.string)
      Some(clientId) <- inject[Signal[AccountManager]].flatMap(_.clientId).head
      _              <- lifecycle.uiActive.collect { case true => () }.head
      _              <- Future {
                          KotlinServices.INSTANCE.createBackup(self.id.str, clientId.str, userHandle, password.str, onBackupSuccess _, onBackupFailed _)
                        }(Threading.Background)
    } yield ()
  }

  private def onBackupFailed(err: String): Unit = Future {
    spinnerController.hideSpinner()
    ViewUtils.showAlertDialog(
      getContext,
      R.string.export_generic_error_title,
      R.string.export_generic_error_text,
      android.R.string.ok,
      null,
      true
    )
  }(Threading.Ui)

  private def onBackupSuccess(file: File): Unit = Future {
    if (isShown) {
      val fileUri = sharing.getUriForFile(file)
      val intent = ShareCompat.IntentBuilder.from(context.asInstanceOf[Activity]).setType("application/octet-stream").setStream(fileUri).getIntent
      context.startActivity(intent)
      spinnerController.hideSpinner(Some(ContextUtils.getString(R.string.back_up_progress_complete)))
    } else
      spinnerController.hideSpinner()
  }(Threading.Ui)
}

case class BackupExportKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_backup_screen_title

  override def layoutId = R.layout.preferences_backup_export

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
