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
package com.waz.zclient.participants.fragments

import android.content.{ClipData, ClipboardManager, Context, DialogInterface}
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.error
import com.waz.api.Verification
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.sync.SyncResult._
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.ClientsController.getDeviceClassName
import com.waz.zclient.common.controllers.global.{AccentColorController, ClientsController}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils.{getBoldHighlightText, getHighlightText}
import com.waz.zclient.ui.views.e2ee.OtrSwitch
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}

class SingleOtrClientFragment extends FragmentHelper {

  import SingleOtrClientFragment._
  implicit def cxt: Context = getActivity

  private lazy val clientsController = inject[ClientsController]
  private lazy val usersController   = inject[UsersController]

  private lazy val userId      = Option(getArguments).map(args => UserId(args.getString(ArgUser)))
  private lazy val clientId    = Option(getArguments).map(args => ClientId(args.getString(ArgClient)))
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)

  private lazy val client = ((userId, clientId) match {
    case (Some(uId), Some(cId)) => clientsController.client(uId, cId)
    case _                      => clientsController.selfClient
  }).collect { case Some(c) => c }

  private lazy val typeTextView = returning(view[TextView](R.id.client_type)) { vh =>
    client
      .map(_.devType)
      .map(getDeviceClassName)
      .map(_.toUpperCase())
      .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val idTextView = returning(view[TypefaceTextView](R.id.client_id)) { vh =>
    client.map(_.id).onUi { clientId =>
      vh.foreach(v => v.setText(ClientsController.getFormattedDisplayId(clientId, v.getCurrentTextColor)))
    }
  }

  private lazy val closeButton = returning(view[TextView](R.id.close)) { vh =>
    vh.onClick(_ => close())
    vh.foreach(_.setVisible(userId.isEmpty))
  }

  private lazy val backButton = returning(view[TextView](R.id.back)) { vh =>
    vh.onClick(_ => close())
    vh.foreach(_.setVisible(userId.isDefined))
  }

  private lazy val verifySwitch = returning(view[OtrSwitch](R.id.verify_switch)) { vh =>
    client.map(_.verified == Verification.VERIFIED).onUi(ver => vh.foreach(_.setChecked(ver)))
    vh.foreach(_.setVisible(userId.isDefined))
  }

  private lazy val descriptionText = returning(view[TextView] (R.id.client_description)) { vh =>
    (userId match {
      case Some(uId) =>
        usersController.user(uId).map(u => getString(R.string.otr__participant__single_device__description, u.getDisplayName))
      case _ =>
        Signal.const(getString(R.string.otr__participant__my_device__description))
    }).onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val howToLinkButton = returning(view[TextView](R.id.how_to_link)) { vh =>
    accentColor
      .map(c => getHighlightText(getActivity, getString(R.string.otr__participant__single_device__how_to_link), c, false))
      .onUi(t => vh.foreach(_.setText(t)))
    vh.onClick(_ => inject[BrowserController].openUrl(getString(R.string.url_otr_learn_how)))
    vh.foreach(_.setVisible(userId.isDefined))
  }

  private lazy val resetSessionButton = returning(view[TextView](R.id.client_reset)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
    vh.onClick(_ => resetSession())
    vh.foreach(_.setVisible(userId.isDefined))
  }

  private lazy val fingerprintView = returning(view[TypefaceTextView](R.id.fingerprint)) { vh =>
    val fingerPrint = ((userId, clientId) match {
      case (Some(uId), Some(cId)) => clientsController.fingerprint(uId, cId)
      case _                      => clientsController.selfFingerprint
    }).collect {
      case Some(fp) => fp
    }

    fingerPrint.map(ClientsController.getFormattedFingerprint).onUi { s =>
      vh.foreach(v => v.setText(getBoldHighlightText(getContext, s, v.getCurrentTextColor, 0, s.length)))
    }

    vh.onClick { v =>
      val clipboard = getActivity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
      val clip = ClipData.newPlainText(getString(R.string.pref_devices_device_fingerprint_copy_description), v.getText.toString)
      clipboard.setPrimaryClip(clip)
      showToast(R.string.pref_devices_device_fingerprint_copy_toast)
    }
  }

  private lazy val myFingerprintButton = returning(view[TextView](R.id.my_fingerprint)){ vh =>
    vh.onClick { _ =>
      Option(getParentFragment).foreach {
        case f: ParticipantFragment => f.showCurrentOtrClient()
        case _ =>
      }
    }

    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
    vh.foreach(_.setVisible(userId.isDefined))
  }

  private lazy val myDevicesButton = returning(view[TextView](R.id.my_devices)) { vh =>
    vh.onClick { _ =>
      startActivity(ShowDevicesIntent(getActivity))
    }

    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
    vh.foreach(_.setVisible(userId.isEmpty))
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_single_otr_client, viewGroup, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    closeButton
    backButton
    myFingerprintButton
    verifySwitch
    descriptionText
    howToLinkButton
    resetSessionButton
    fingerprintView
    typeTextView
    idTextView
    myDevicesButton
  }

  private def close() = {
    getFragmentManager.popBackStackImmediate
  }

  private def resetSession(): Unit = (userId, clientId) match {
    case (Some(uId), Some(cId)) =>
      resetSessionButton.foreach(_.setEnabled(false))
      clientsController.resetSession(uId, cId).map { res =>
        resetSessionButton.foreach(_.setEnabled(true))
        res match {
          case Success =>
            ViewUtils.showAlertDialog(
              getActivity,
              R.string.empty_string,
              R.string.otr__reset_session__message_ok,
              R.string.otr__reset_session__button_ok, null, true)
          case Failure(_) =>
            ViewUtils.showAlertDialog(
              getActivity,
              R.string.empty_string,
              R.string.otr__reset_session__message_fail,
              R.string.otr__reset_session__button_ok,
              R.string.otr__reset_session__button_fail,
              null,
              new DialogInterface.OnClickListener() {
                override def onClick(dialog: DialogInterface, which: Int) = {
                  resetSession()
                }
              })
          case Retry(_) =>
            error("Awaiting a sync job should not return Retry")//TODO return ErrorOr[Unit] from await?
        }
      } (Threading.Ui)
    case _ =>
  }

  override def onResume() = {
    super.onResume()
    verifySwitch.foreach(_.setOnCheckedListener(new CompoundButton.OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = (userId, clientId) match {
        case (Some(uId), Some(cId)) => clientsController.updateVerified(uId, cId, isChecked)
        case _ =>
      }
    }))
  }

  override def onPause() = {
    verifySwitch.foreach(_.setOnCheckedListener(null))
    super.onPause()
  }

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    inject[IConversationScreenController].hideOtrClient()
    true
  }
}

object SingleOtrClientFragment {
  val Tag = classOf[SingleOtrClientFragment].getName
  private val ArgUser = "ARG_USER"
  private val ArgClient = "ARG_CLIENT"

  def newInstance: SingleOtrClientFragment = new SingleOtrClientFragment

  def newInstance(userId: UserId, clientId: ClientId): SingleOtrClientFragment = {
    returning(new SingleOtrClientFragment) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putString(ArgUser, userId.str)
        b.putString(ArgClient, clientId.str)
      })
    }
  }
}
