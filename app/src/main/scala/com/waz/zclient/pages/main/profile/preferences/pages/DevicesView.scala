/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.profile.preferences.pages

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, ScrollView}
import com.waz.model.otr.Client
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.pages.main.profile.preferences.views.DeviceButton
import com.waz.zclient.utils.ViewState
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

trait DevicesView {
  def setSelfDevice(device: Client): Unit
  def setOtherDevices(devices: Seq[Client]): Unit
}

class DevicesViewImpl(context: Context, attrs: AttributeSet, style: Int) extends ScrollView(context, attrs, style) with DevicesView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_devices_layout)

  val selfDeviceButton = findById[DeviceButton](R.id.current_device)
  val deviceList = findById[LinearLayout](R.id.device_list)

  override def setSelfDevice(device: Client): Unit = {
    selfDeviceButton.setDevice(device, self = true)
  }

  override def setOtherDevices(devices: Seq[Client]): Unit = {
    deviceList.removeAllViews()
    devices.foreach{ device =>
      val deviceButton = new DeviceButton(context, attrs, style)
      deviceButton.setDevice(device, self = false)
      deviceList.addView(deviceButton)
    }
  }
}

case class DevicesViewState() extends ViewState {
  override def name = "Devices"

  override def layoutId = R.layout.preferences_devices

  private var controller = Option.empty[DevicesViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[DevicesViewImpl]).map(view => DevicesViewController(view)(view.injector, view))
  }

  override def onViewDetached() = controller = None
}

case class DevicesViewController(view: DevicesView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  val clients = for {
    zms <- zms
    clients <- zms.otrClientsStorage.signal(zms.selfUserId)
    selfClient <- zms.otrClientsService.selfClient
  } yield (selfClient, clients.clients.values.filter(_.id != selfClient.id).toSeq.sortBy(_.regTime))

  clients.on(Threading.Ui){
    case (selfClient, otherClients) =>
      view.setSelfDevice(selfClient)
      view.setOtherDevices(otherClients)
    case _=>
  }
}
