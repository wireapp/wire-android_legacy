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

import android.app.{AlertDialog, FragmentTransaction}
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.content.GlobalPreferences._
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.jobs.PushTokenCheckJob
import com.waz.model.AccountData.Password
import com.waz.model.Uid
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils.BackStackKey
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.{BaseActivity, R, ViewHelper}

import scala.concurrent.Future

trait DevSettingsView

class DevSettingsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with DevSettingsView with ViewHelper {
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

  val slowSyncButton = returning(findById[TextButton](R.id.preferences_dev_slow_sync)) {
    _.onClickEvent { _ =>
      zms.head.flatMap(_.sync.performFullSync())
    }
  }

  val registerAnotherClient = returning(findById[TextButton](R.id.register_another_client)) { v =>
    v.onClickEvent { v =>
      registerClient(v)
    }
  }

  val createFullConversationSwitch = returning(findById[SwitchPreference](R.id.preferences_dev_full_conv)) { v =>
    v.setPreference(ShouldCreateFullConversation, global = true)
  }

  val checkPushTokenButton = returning(findById[TextButton](R.id.preferences_dev_check_push_tokens)) { v =>
    v.onClickEvent(_ => PushTokenCheckJob())
  }

  private def registerClient(v: View, password: Option[Password] = None): Future[Unit] = {
    am.head.flatMap(_.registerNewClient()).map {
      case Right(Registered(id)) => showToast(s"Registered new client: $id")
      case Right(PasswordMissing) =>
        inject[PasswordController].password.head.map {
          case Some(p) => registerClient(v, Some(p))
          case _ => showPasswordDialog(v)
        }
      case Right(LimitReached) => v.setEnabled(false)
      case Right(Unregistered) => showToast("Something went wrong, failed to register client")
      case Left(err) =>
        showPasswordDialog(v, Some(err.message))
    }
  }

  private def showPasswordDialog(v: View, error: Option[String] = None): Unit = {
    val fragment = returning(RequestPasswordDialog.newInstance(error))(_.onPassword(p => registerClient(v, Some(p))))
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(fragment, RequestPasswordDialog.Tag)
      .addToBackStack(RequestPasswordDialog.Tag)
      .commit
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
