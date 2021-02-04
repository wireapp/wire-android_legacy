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
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.core.content.ContextCompat
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.waz.model.AccountData.Password
import com.waz.model.UserId
import com.waz.permissions.PermissionsService
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.utils.{returning, _}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.fragments.FirstLaunchAfterLoginFragment._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog.InputPasswordMode
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient._
import com.wire.signals.CancellableFuture

import scala.collection.immutable.ListSet
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

object FirstLaunchAfterLoginFragment {
  val Tag: String = classOf[FirstLaunchAfterLoginFragment].getName
  val UserIdArg = "user_id_arg"
  val SSOHadDBArg = "sso_had_db_arg"

  def apply(userId: UserId, ssoHadDB: Boolean = true): Fragment = returning(new FirstLaunchAfterLoginFragment) { f =>
    val bundle = new Bundle()
    bundle.putString(UserIdArg, userId.str)
    bundle.putBoolean(SSOHadDBArg, ssoHadDB)
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

  private lazy val userIdArg = getStringArg(UserIdArg).map(UserId(_))

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(`type`: AssetIntentsManager.IntentType, uri: URI): Unit = {
      userIdArg.map(enter(_, Some(uri)))
    }
    override def onCanceled(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def onFailed(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Boolean =
      safeStartActivityForResult(intent, intentType.requestCode)
  }

  private var assetIntentsManager = Option.empty[AssetIntentsManager]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback))
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
    if (databaseExists && getBooleanArg(SSOHadDBArg)) {
      infoTitle.foreach(_.setText(R.string.second_launch__header))
      infoText.foreach(_.setText(R.string.second_launch__sub_header))
    }
  }

  private def databaseExists = userIdArg.exists(userId => getContext.getDatabasePath(userId.str).exists())

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_login_first_launch, viewGroup, false)

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.zb__first_launch__confirm => userIdArg.map(enterWithoutBackup)
      case R.id.restore_button => importBackup()
    }
  }

  private def importBackup(): Unit = {
    def openBackupChooser() = {
      permissions.requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).foreach { granted =>
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

    if (databaseExists && getBooleanArg(SSOHadDBArg)) showBackupConfirmationDialog
    else openBackupChooser()
  }

  private def displayError(title: Int, text: Int) =
    ViewUtils.showAlertDialog(getContext, title, text, android.R.string.ok, null, true)

  private def enter(userId: UserId, backup: Option[URI]) = backup match {
    case Some(backupUri) =>
      getBackupFile(backupUri).map { backupFile =>
        val fragment = returning(BackupPasswordDialog.newInstance(InputPasswordMode)) {
          _.onPasswordEntered(password => enterWithBackup(userId, backupFile, password))
        }
        getActivity.asInstanceOf[BaseActivity]
          .getSupportFragmentManager
          .beginTransaction
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          .add(fragment, BackupPasswordDialog.FragmentTag)
          .addToBackStack(BackupPasswordDialog.FragmentTag)
          .commit
      }.recover {
        case ex =>
          error(l"Unable to find backup file $backupUri", ex)
          displayError(R.string.backup_import_error_unknown_title, R.string.backup_import_error_unknown)
      }
    case _ =>
      enterWithoutBackup(userId)
  }

  private def getBackupFile(backupUri: URI) = Try {
    val inputStream = getContext.getContentResolver.openInputStream(AndroidURIUtil.unwrap(backupUri))
    val file = File.createTempFile("wire", null)
    val outputStream = new FileOutputStream(file)
    IoUtils.copy(inputStream, outputStream)
    file
  }

  private def enterWithBackup(userId: UserId, backupFile: File, backupPassword: Password): Future[Unit] = {
    import com.waz.zclient.utils.ScalaToKotlin._

    spinnerController.showDimmedSpinner(show = true, getString(R.string.restore_progress))

    val promise = Promise[Unit]
    case class BackupError(reason: String) extends Throwable

    def onSuccess(): Unit = {
      promise.success(())
    }

    def onFailure(reason: String): Unit = {
      promise.failure(BackupError(reason))
    }

    (for {
      Some(accountManager) <- accountsService.createAccountManager(userId, isLogin = Some(true))
      _                    =  accountManager.addUnsplashIfProfilePictureMissing()
      _                    <- accountsService.setAccount(Some(userId))
      _                    <- Future {
                                KotlinServices.INSTANCE.restoreBackup(backupFile, userId.str, backupPassword.str, onSuccess _, onFailure _)
                              }(Threading.Background)
      _                    <- promise.future
      _                    =  backupFile.delete()
      registrationState    <- accountManager.getOrRegisterClient()
      Some(zms)            <- accountsService.getZms(userId)
      _                    <- zms.sync.performFullSync()
      _                    =  spinnerController.hideSpinner(Some(getString(R.string.back_up_progress_complete)))
      _                    <- CancellableFuture.delay(750.millis).future
    } yield registrationState match {
      case Right(regState) => activity.onEnterApplication(openSettings = false, Some(regState))
      case _               => activity.onEnterApplication(openSettings = false)
    }).recover {
      case BackupError(reason) =>
        error(l"Unable to restore backup: $reason")
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unknown_title, R.string.backup_import_error_unknown)
      case e =>
        println(s"Got error: ${e.getMessage}")
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unknown_title, R.string.backup_import_error_unknown)
    }
  }

  private def enterWithoutBackup(userId: UserId): Future[Unit] = {
    spinnerController.showDimmedSpinner(show = true, "")
    for {
      Some(accountManager) <- accountsService.createAccountManager(userId, isLogin = Some(true))
      _                    =  accountManager.addUnsplashIfProfilePictureMissing()
      _                    <- accountsService.setAccount(Some(userId))
      registrationState    <- accountManager.getOrRegisterClient()
      _                    =  spinnerController.hideSpinner(Some(getString(R.string.back_up_progress_complete)))
      _                    <- CancellableFuture.delay(750.millis).future
    } yield registrationState match {
      case Right(regState) => activity.onEnterApplication(openSettings = false, Some(regState))
      case _               => activity.onEnterApplication(openSettings = false)
    }
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    assetIntentsManager.foreach(_.onActivityResult(requestCode, resultCode, data))
  }
}
