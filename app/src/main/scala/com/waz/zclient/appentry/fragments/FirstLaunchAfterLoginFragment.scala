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

package com.waz.zclient.appentry.fragments

import java.io.{File, FileOutputStream}

import android.Manifest.permission._
import android.content.{DialogInterface, Intent}
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.model.UserId
import com.waz.permissions.PermissionsService
import com.waz.service.AccountsService
import com.waz.service.BackupManager.InvalidMetadata
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.utils.{RichFuture, returning, _}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.fragments.FirstLaunchAfterLoginFragment._
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, R, SpinnerController}
import scala.concurrent.duration._

import scala.async.Async.{async, await}
import scala.concurrent.Future

object FirstLaunchAfterLoginFragment {
  val Tag: String = classOf[FirstLaunchAfterLoginFragment].getName
  val UserIdArg = "user_id_arg"

  def apply(): Fragment = new FirstLaunchAfterLoginFragment
  def apply(userId: UserId): Fragment = returning(new FirstLaunchAfterLoginFragment) { f =>
    val bundle = new Bundle()
    bundle.putString(UserIdArg, userId.str)
    f.setArguments(bundle)
  }
}

class FirstLaunchAfterLoginFragment extends FragmentHelper with View.OnClickListener {

  implicit val ec = Threading.Ui

  private lazy val accountsService    = inject[AccountsService]
  private lazy val permissions        = inject[PermissionsService]
  private lazy val spinnerController  = inject[SpinnerController]

  private lazy val restoreButton = view[ZetaButton](R.id.restore_button)
  private lazy val registerButton = view[ZetaButton](R.id.zb__first_launch__confirm)
  private lazy val infoTitle = view[TypefaceTextView](R.id.info_title)
  private lazy val infoText = view[TypefaceTextView](R.id.info_text)

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(`type`: AssetIntentsManager.IntentType, uri: URI): Unit = {
      enter(Some(uri))
    }
    override def onCanceled(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def onFailed(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {
      startActivityForResult(intent, intentType.requestCode)
    }
  }

  private var assetIntentsManager = Option.empty[AssetIntentsManager]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback, savedInstanceState))
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    registerButton.foreach { registerButton =>
      registerButton.setOnClickListener(this)
      registerButton.setIsFilled(true)
      registerButton.setAccentColor(ContextCompat.getColor(getContext, R.color.text__primary_dark))
    }
    restoreButton.foreach{ restoreButton =>
      restoreButton.setOnClickListener(this)
      restoreButton.setIsFilled(false)
      restoreButton.setAccentColor(ContextCompat.getColor(getContext, R.color.text__primary_dark))
    }
    if (databaseExists) {
      infoTitle.foreach(_.setText(R.string.second_launch__header))
      infoText.foreach(_.setText(R.string.second_launch__sub_header))
    }
  }

  private def databaseExists = getStringArg(UserIdArg).exists(userId => getContext.getDatabasePath(userId).exists())

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_login_first_launch, viewGroup, false)

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.zb__first_launch__confirm => enter(None)
      case R.id.restore_button => importBackup()
    }
  }

  private def importBackup(): Unit = {
    def openBackupChooser() = {
      permissions.requestAllPermissions(Set(READ_EXTERNAL_STORAGE)).foreach { granted =>
        if (granted) assetIntentsManager.foreach(_.openBackupImport())
        else {
          //todo show something???
        }
      }
    }
    def showBackupConfirmationDialog = ViewUtils.showAlertDialog(
      getContext,
      R.string.restore_override_alert_title,
      R.string.restore_override_alert_text,
      R.string.restore_override_alert_ok,
      android.R.string.cancel,
      new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = openBackupChooser()
      },
      null
    )

    if (databaseExists) showBackupConfirmationDialog
    else openBackupChooser()
  }

  private def displayError(title: Int, text: Int) =
    ViewUtils.showAlertDialog(getContext, title, text, android.R.string.ok, null, true)

  private def enter(backup: Option[URI]) = {
    spinnerController.showDimmedSpinner(show = true, if (backup.isDefined) getString(R.string.restore_progress) else "")
    async {
      val userId = getStringArg(UserIdArg).map(UserId(_))
      if (userId.nonEmpty) {

        val backupFile = backup.map { uri =>
          val inputStream = getContext.getContentResolver.openInputStream(AndroidURIUtil.unwrap(uri))
          val file = File.createTempFile("wire", null)
          val outputStream = new FileOutputStream(file)
          IoUtils.copy(inputStream, outputStream)
          file
        }

        val accountManager = await(accountsService.createAccountManager(userId.get, backupFile, isLogin = Some(true)))
        backupFile.foreach(_.delete())
        await { accountsService.setAccount(userId) }
        val registrationState = await { accountManager.fold2(Future.successful(Left(ErrorResponse.internalError(""))), _.getOrRegisterClient()) }
        if (backup.isDefined) {
          spinnerController.hideSpinner(Some(getString(R.string.back_up_progress_complete)))
          await { CancellableFuture.delay(750.millis).future }
        }
        registrationState match {
          case Right(regState) => activity.onEnterApplication(openSettings = false, Some(regState))
          case _ => activity.onEnterApplication(openSettings = false)
        }
      }
    } logFailure() recover {
      case InvalidMetadata.UserId =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_wrong_account_title, R.string.backup_import_error_wrong_account)
      case _: InvalidMetadata =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unsupported_version_title, R.string.backup_import_error_unsupported_version)
      case _ =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unknown_title, R.string.backup_import_error_unknown)
    }
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    assetIntentsManager.foreach(_.onActivityResult(requestCode, resultCode, data))
  }
}
