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
import android.content.DialogInterface.OnClickListener
import android.content.{ClipData, Context, DialogInterface}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, ScrollView, Toast}
import androidx.fragment.app.FragmentTransaction
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.{ConvId, QualifiedId}
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.AccountManager.ClientRegistrationState.LimitReached
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.sync.SyncResult
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.{ClientsController, PasswordController}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.DevicesPreferencesUtil
import com.waz.zclient.preferences.dialogs.RemoveDeviceDialog
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils.{BackStackKey, RichClient, RichView, ViewUtils}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper, _}
import com.wire.signals.{EventContext, EventStream, Signal}
import org.threeten.bp.Instant
import com.waz.zclient.BuildConfig

import scala.concurrent.Future
import scala.util.Try

trait DeviceDetailsView {
  val onVerifiedChecked: EventStream[Boolean]
  val onSessionReset:    EventStream[Unit]
  val onDeviceRemoved:   EventStream[Unit]

  def setName(name: String): Unit
  def setId(cId: String): Unit
  def setActivated(regTime: Instant): Unit
  def setFingerPrint(fingerprint: String): Unit
  def setRemoveOnly(removeOnly: Boolean): Unit
  def setActionsVisible(visible: Boolean): Unit
  def setVerified(verified: Boolean): Unit
  def setRemovable(removable: Boolean): Unit

  //TODO make a super trait for these?
  def showToast(rId: Int): Unit
  def showDialog(msg: Int, positive: Int, negative: Int, onNeg: => Unit = {}, onPos: => Unit = {}): Unit
}

class DeviceDetailsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends ScrollView(context, attrs, style) with DeviceDetailsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val clipboard = inject[ClipboardUtils]

  inflate(R.layout.preferences_device_details_layout)

  val nameView         = findById[TypefaceTextView](R.id.device_detail_name)
  val idView           = findById[TypefaceTextView](R.id.device_detail_id)
  val activatedView    = findById[TypefaceTextView](R.id.device_detail_activated)
  val fingerprintView  = findById[TextButton]      (R.id.device_detail_fingerprint)
  val actionsView      = findById[LinearLayout]    (R.id.device_detail_actions)
  val verifiedSwitch   = findById[SwitchPreference](R.id.device_detail_verified)
  val resetSessionView = findById[TextButton]      (R.id.device_detail_reset)
  val removeDeviceView = findById[TextButton]      (R.id.device_detail_remove)

  val fingerPrintGroup = findById[View](R.id.fingerprint_group)

  override val onVerifiedChecked = verifiedSwitch.onCheckedChange
  override val onSessionReset    = resetSessionView.onClickEvent.map(_ => {})
  override val onDeviceRemoved   = removeDeviceView.onClickEvent.map(_ => {})

  private var fingerprint = ""

  override def setName(name: String) = {
    nameView.setText(name)
    TextViewUtils.boldText(nameView)
  }

  override def setId(cId: String) = {
    idView.setText(cId)
    TextViewUtils.boldText(idView)
  }

  override def setActivated(regTime: Instant) = {
    activatedView.setText(getString(R.string.pref_devices_device_activation_summary, TimeStamp(regTime).string))
    TextViewUtils.boldText(activatedView)
  }

  override def setFingerPrint(fingerprint: String) = {
    this.fingerprint = fingerprint
    fingerprintView.setTitle(DevicesPreferencesUtil.getFormattedFingerprint(context, fingerprint).toString)
    fingerprintView.title.foreach(TextViewUtils.boldText)
    fingerprintView.setTitleMaxLines(2)
  }

  override def setActionsVisible(visible: Boolean) = {
    actionsView.setVisible(visible)
  }

  override def setRemoveOnly(removeOnly: Boolean) = {
    fingerPrintGroup.setVisible(!removeOnly)
    verifiedSwitch.setVisible(!removeOnly)
    resetSessionView.setVisible(!removeOnly)
  }

  fingerprintView.onClickEvent.onUi { _ =>
    val clip = ClipData.newPlainText(getContext.getString(R.string.pref_devices_device_fingerprint_copy_description), fingerprint)
    clipboard.setPrimaryClip(clip)
    showToast(R.string.pref_devices_device_fingerprint_copy_toast)
  }

  override def setVerified(verified: Boolean) =
    verifiedSwitch.setChecked(verified)

  override def setRemovable(removable: Boolean): Unit =
    removeDeviceView.setVisible(removable)

  override def showToast(rId: Int) =
    Toast.makeText(getContext, rId, Toast.LENGTH_LONG).show()

  override def showDialog(msg: Int, positive: Int, negative: Int, onNeg: => Unit = {}, onPos: => Unit = {}) = {
    Try(getContext.asInstanceOf[Activity]).toOption.foreach { a =>
      ViewUtils.showAlertDialog(a, R.string.empty_string, msg, positive, negative,
        new OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit = onNeg
        },
        new OnClickListener() {
          override def onClick(dialog: DialogInterface, which: Int) = onPos
        })
    }
  }
}

case class DeviceDetailsBackStackKey(args: Bundle) extends BackStackKey(args) {
  import DeviceDetailsBackStackKey._

  val deviceId = ClientId(args.getString(DeviceIdKey, ""))
  var controller = Option.empty[DeviceDetailsViewController]

  override def nameId = R.string.pref_devices_device_screen_title

  override def layoutId = R.layout.preferences_device_details

  override def onViewAttached(v: View) =
    controller = Option(v.asInstanceOf[DeviceDetailsViewImpl]).map(view => DeviceDetailsViewController(view, deviceId)(view.injector, view.eventContext, v.getContext))

  override def onViewDetached() = {
    controller = None
  }
}

object DeviceDetailsBackStackKey {
  private val DeviceIdKey = "DeviceIdKey"
  def apply(deviceId: String): DeviceDetailsBackStackKey = {
    val bundle = new Bundle()
    bundle.putString(DeviceIdKey, deviceId)
    DeviceDetailsBackStackKey(bundle)
  }
}

case class DeviceDetailsViewController(view: DeviceDetailsView, clientId: ClientId)(implicit inj: Injector, ec: EventContext, context: Context)
  extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val zms                = inject[Signal[ZMessaging]]
  private lazy val clientsController  = inject[ClientsController]
  private lazy val spinnerController  = inject[SpinnerController]

  private lazy val accountManager  = inject[Signal[AccountManager]]
  private lazy val accountsService = inject[AccountsService]

  private lazy val client = clientsController.selfClient(clientId).collect { case Some(c) => c }

  client.map(deviceName).onUi(view.setName)
  client.map(_.displayId).onUi(view.setId)
  client.map(_.regTime.getOrElse(Instant.EPOCH)).onUi(view.setActivated)

  clientsController.isCurrentClient(clientId).map(!_).onUi(view.setActionsVisible)
  client.map(_.isVerified).onUi(view.setVerified)
  client.map(canRemoveDevice).onUi(view.setRemovable)
  clientsController.selfFingerprint(clientId).onUi{ _.foreach(view.setFingerPrint) }
  clientsController.selfClientId.map(_.isEmpty).onUi(view.setRemoveOnly)

  view.onVerifiedChecked.onUi { checked =>
    //TODO should this be a signal? Will create a new subscription every time the view is clicked...
    for {
      userId     <- accountManager.map(_.userId)
      otrStorage <- accountManager.map(_.storage.otrClientsStorage)
      _ <- otrStorage.updateVerified(userId, clientId, checked)
    } {}
  }

  view.onSessionReset.onUi(_ => resetSession())

  private def resetSession(): Unit = {
    zms.head.flatMap { zms =>
      zms.selectedConv.selectedConvIdPref().flatMap { conv =>
        (if (BuildConfig.FEDERATION_USER_DISCOVERY) {
          val qId = QualifiedId(zms.selfUserId, zms.selfDomain.str)
          zms.otrService.resetSession(conv.getOrElse(ConvId(zms.selfUserId.str)), qId, clientId)
        } else {
          zms.otrService.resetSession(conv.getOrElse(ConvId(zms.selfUserId.str)), zms.selfUserId, clientId)
        })
          .flatMap(zms.syncRequests.await)
      }
    }.recover {
      case e: Throwable => SyncResult(e)
    }.map {
      case SyncResult.Success      => view.showToast(R.string.otr__reset_session__message_ok)
      case SyncResult.Failure(err) =>
        warn(l"session reset failed: $err")
        view.showDialog(R.string.otr__reset_session__message_fail, R.string.otr__reset_session__button_ok, R.string.otr__reset_session__button_fail, onPos = resetSession())
      case SyncResult.Retry(err)   =>
        error(l"Await sync result shouldn't return retry: $err")
    }(Threading.Ui)
  }

  view.onDeviceRemoved.onUi { _ =>
    accountsService.accountPassword.head.map {
      case Some(p) => removeDevice(Some(p))
      case _       => showRemoveDeviceDialog()
    }
  }

  private def removeDevice(password: Option[Password] = None): Unit = {
    spinnerController.showDimmedSpinner(show = true)
    for {
      am           <- accountManager.head
      limitReached <- am.clientState.head.map {
        case LimitReached => true
        case _ => false
      }
      _ <- am.deleteClient(clientId, password).map {
        case Right(_) =>
          for {
            _ <- if (limitReached) am.getOrRegisterClient() else Future.successful({})
            _ <- Threading.Ui {
              spinnerController.showSpinner(false)
              context.asInstanceOf[BaseActivity].onBackPressed()
            }
          } yield {}
        case Left(_) =>
          Threading.Ui {
            spinnerController.showSpinner(false)
            showRemoveDeviceDialog(Some(getString(R.string.otr__remove_device__error)))
          }
      }
    } yield ()
  }

  private def showRemoveDeviceDialog(error: Option[String] = None): Unit =
    Signal.zip(inject[PasswordController].ssoEnabled, client.map(deviceName)).head.foreach { case (isSSO, name) =>
      val fragment = returning(RemoveDeviceDialog.newInstance(name, error, isSSO))(_.onAccept.onUi(removeDevice))
      context.asInstanceOf[BaseActivity]
        .getSupportFragmentManager
        .beginTransaction
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .add(fragment, RemoveDeviceDialog.FragmentTag)
        .addToBackStack(RemoveDeviceDialog.FragmentTag)
        .commit
    } (Threading.Ui)

  private def canRemoveDevice(client: Client): Boolean = !client.isLegalHoldDevice

  private def deviceName(client: Client): String =
    if (client.isLegalHoldDevice) getString(R.string.legal_hold_device_name)
    else client.model
}
