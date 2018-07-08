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

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.{LinearLayout, ScrollView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.otr.Client
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.preferences.views.DeviceButton
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

trait DevicesView {
  def setSelfDevice(device: Option[Client]): Unit
  def setOtherDevices(devices: Seq[Client]): Unit
}

class DevicesViewImpl(context: Context, attrs: AttributeSet, style: Int) extends ScrollView(context, attrs, style) with DevicesView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_devices_layout)

  private val navigator = inject[BackStackNavigator]

  val currentDeviceTitle = findById[TypefaceTextView](R.id.current_device_title)
  val selfDeviceButton = findById[DeviceButton](R.id.current_device)
  val deviceList = findById[LinearLayout](R.id.device_list)

  override def setSelfDevice(device: Option[Client]): Unit = {
    device.fold {
      selfDeviceButton.setVisibility(View.GONE)
      currentDeviceTitle.setVisibility(View.GONE)
    } { device =>
      selfDeviceButton.setVisibility(View.VISIBLE)
      currentDeviceTitle.setVisibility(View.VISIBLE)
      selfDeviceButton.setDevice(device, self = true)
      selfDeviceButton.onClickEvent { _ => navigator.goTo(DeviceDetailsBackStackKey(device.id.str)) }
    }

  }

  override def setOtherDevices(devices: Seq[Client]): Unit = {
    deviceList.removeAllViews()
    devices.foreach{ device =>
      val deviceButton = new DeviceButton(context, attrs, style)
      deviceButton.setDevice(device, self = false)
      deviceButton.onClickEvent { _ => navigator.goTo(DeviceDetailsBackStackKey(device.id.str)) }
      deviceList.addView(deviceButton)
      val margin = context.getResources.getDimensionPixelSize(R.dimen.wire__padding__8)
      Option(deviceButton.getLayoutParams.asInstanceOf[MarginLayoutParams]).foreach(_.setMargins(0, 0, 0, margin))
    }
  }
}

case class DevicesBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_devices_screen_title

  override def layoutId = R.layout.preferences_devices

  private var controller = Option.empty[DevicesViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[DevicesViewImpl]).map(view => DevicesViewController(view)(view.injector, view))
  }

  override def onViewDetached() = {
    controller.foreach(_.onViewClose())
    controller = None
  }
}

case class DevicesViewController(view: DevicesView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[Option[ZMessaging]]]
  val accounts = inject[AccountsService]

  val otherClients = for {
    Some(am)      <- accounts.activeAccountManager
    selfClientId  <- am.clientId
    clients       <- Signal.future(am.storage.otrClientsStorage.get(am.userId))
  } yield clients.fold(Seq[Client]())(_.clients.values.filter(client => !selfClientId.contains(client.id)).toSeq.sortBy(_.regTime).reverse)

  val incomingClients = for {
    Some(am)           <- accounts.activeAccountManager
    Some(selfClientId) <- am.clientId
    clients            <- am.storage.otrClientsStorage.incomingClientsSignal(am.userId, selfClientId)
  } yield clients

  val selfClient = for {
    zms <- zms.orElse(Signal.const(None))
    selfClient <- zms.fold(Signal.const(Option.empty[Client]))(_.otrClientsService.selfClient.map(Option(_)))
  } yield selfClient

  selfClient.onUi(view.setSelfDevice)
  otherClients.onUi(view.setOtherDevices)

  def onViewClose(): Unit = {
    implicit val ec = Threading.Background
    for {
      Some(zms) <- zms.head
      _         <- zms.otrClientsService.updateUnknownToUnverified(zms.selfUserId)
    } ()
  }
}
