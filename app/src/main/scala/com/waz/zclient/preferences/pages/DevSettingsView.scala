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

import android.app.AlertDialog
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences._
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.jobs.PushTokenCheckJob
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.Uid
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog.{PasswordAnswer, PasswordCancelled, PromptAnswer}
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.security.checks.RootDetectionCheck
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.utils.{BackStackKey, ContextUtils}

import scala.concurrent.Future

trait DevSettingsView

class DevSettingsViewImpl(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with DevSettingsView
    with ViewHelper
    with DerivedLogTag {
  
  import com.waz.threading.Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val am  = inject[Signal[AccountManager]]
  val zms = inject[Signal[ZMessaging]]

  inflate(R.layout.preferences_dev_layout)

  val autoAnswerSwitch = returning(findById[SwitchPreference](R.id.preferences_dev_auto_answer)) { v =>
    v.setPreference(AutoAnswerCallPrefKey, global = true)
  }

  val cloudMessagingSwitch = returning(findById[SwitchPreference](R.id.preferences_dev_gcm)) { v =>
    v.setPreference(PushEnabledKey, global = true)
  }

  val randomLastIdButton = findById[TextButton](R.id.preferences_dev_generate_random_lastid)

  val registerAnotherClient = returning(findById[TextButton](R.id.register_another_client)) {
    _.onClickEvent(_ => registerClient().foreach {
      case Right(registered) => if(!registered) dialog.show(context.asInstanceOf[PreferencesActivity])
      case Left(err) => dialog.showError(Some(err))
    })
  }

  val createFullConversationSwitch = returning(findById[SwitchPreference](R.id.preferences_dev_full_conv)) { v =>
    v.setPreference(ShouldCreateFullConversation, global = true)
  }

  val checkPushTokenButton = returning(findById[TextButton](R.id.preferences_dev_check_push_tokens)) { v =>
    v.onClickEvent(_ => PushTokenCheckJob())
  }

  val checkDeviceRootedButton = returning(findById[TextButton](R.id.preferences_dev_check_rooted_device)) { v =>
    v.onClickEvent(_ => checkIfDeviceIsRooted())
  }

  private def checkIfDeviceIsRooted(): Unit = {
    val preferences = inject[GlobalPreferences]
    RootDetectionCheck(preferences).isSatisfied.foreach { notRooted =>
      showToast(s"Device is ${if (notRooted) "not" else ""} rooted")
    }
  }

  /**
    * Used to test how the app handles having multiple client devices connected to one account
    * while we actually don't have access to so many different devices / don't want to waste time.
    *
    * When called, the method tries to create a new fake client on the backend. It may fail for
    * a number of reasons, one of them being that the password is missing or it's invalid. We need
    * to distinguish this reason from others and open RequestPasswordDialog if it happens.
    * Another reason can be that the user reached the maximum allowed number of client devices -
    * in that case we simply disallow to create another one. In other case swe display a toast with
    * the error.
    *
    * @param password optional; password provided from RequestPasswordDialog
    * @return `Either` of an error message (left) or a boolean if the dialog should be opened. Note
    *        that Right(true) might mean that the new client was created, but it also might mean
    *        that the maximum number of clients was reached and therefore we don't want to open
    *        the dialog.
    */
  private def registerClient(password: Option[Password] = None): Future[Either[String, Boolean]] =
    am.head.flatMap(_.registerNewClient(password)).flatMap {
      case Right(Registered(id))  =>
        showToast(s"Registered new client: $id")
        Future.successful(Right(true))
      case Right(PasswordMissing) =>
        inject[PasswordController].password.head.flatMap {
          case Some(p) => registerClient(Some(p))
          case _       => Future.successful(Right(false))
        }
      case Right(LimitReached)   =>
        registerAnotherClient.setEnabled(false)
        Future.successful(Right(true))
      case Right(Unregistered)   =>
        showToast("Something went wrong, failed to register client")
        Future.successful(Right(true))
      case Left(err) =>
        Future.successful(Left(err.message))
    }

  private lazy val dialog = RequestPasswordDialog(
    title    = ContextUtils.getString(R.string.pref_dev_register_new_client_title),
    onAnswer = onAnswer
  )

  private def onAnswer(answer: PromptAnswer): Unit = answer match {
    case PasswordAnswer(password) => registerClient(Some(password)).foreach {
                                       case Right(true)  => dialog.close()
                                       case Right(false) => dialog.showError(None)
                                       case Left(err)    => dialog.showError(Some(err))
                                     }
    case PasswordCancelled => dialog.close()
    case _ => // no biometric prompt allowed
  }

  randomLastIdButton.onClickEvent { _ =>
    val randomUid = Uid()

    new AlertDialog.Builder(context)
      .setTitle("Random new value for LastStableNotification")
      .setMessage(s"Sets LastStableNotification to $randomUid")
      .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          val zms = inject[Signal[ZMessaging]]
          zms.map(_.userPrefs.preference(LastStableNotification)).onUi {
            _ := Some(randomUid)
          }
        }
      })
      .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {}
      })
      .setIcon(android.R.drawable.ic_dialog_alert).show
  }
}

case class DevSettingsBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_developer_screen_title

  override def layoutId = R.layout.preferences_dev

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
