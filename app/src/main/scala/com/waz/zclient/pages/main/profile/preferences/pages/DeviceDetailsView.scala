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

import android.content.{ClipData, ClipboardManager, Context}
import android.os.Bundle
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, ScrollView, Toast}
import com.waz.model.otr.{ClientId, Location}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.pages.main.profile.preferences.DevicesPreferencesUtil
import com.waz.zclient.pages.main.profile.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.{BackStackKey, RichView, ZTimeFormatter}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import org.threeten.bp.{Instant, LocalDateTime, ZoneId}

trait DeviceDetailsView {
  def setName(name: String)
  def setId(id: String)
  def setActivated(regTime: Instant, regLocation: Option[Location])
  def setFingerPrint(fingerprint: String)
  def setActionsVisible(visible: Boolean)
  def setVerified(verified: Boolean)
}

class DeviceDetailsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends ScrollView(context, attrs, style) with DeviceDetailsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_device_details_layout)

  val nameView = findById[TypefaceTextView](R.id.device_detail_name)
  val idView = findById[TypefaceTextView](R.id.device_detail_id)
  val activatedView = findById[TypefaceTextView](R.id.device_detail_activated)
  val fingerprintView = findById[TextButton](R.id.device_detail_fingerprint)
  val actionsView = findById[LinearLayout](R.id.device_detail_actions)
  val verifiedSwitch = findById[SwitchPreference](R.id.device_detail_verified)

  private var fingerprint = ""

  override def setName(name: String) = {
    nameView.setText(name)
    TextViewUtils.boldText(nameView)
  }

  override def setId(id: String) = {
    idView.setText(id)
    TextViewUtils.boldText(idView)
  }

  override def setActivated(regTime: Instant, regLocation: Option[Location]) = {
    activatedView.setText(getActivationSummary(context, regTime, regLocation))
    TextViewUtils.boldText(activatedView)
  }

  override def setFingerPrint(fingerprint: String) = {
    this.fingerprint = fingerprint
    fingerprintView.setTitle(DevicesPreferencesUtil.getFormattedFingerprint(context, fingerprint).toString)
    fingerprintView.title.foreach(TextViewUtils.boldText)
  }

  override def setActionsVisible(visible: Boolean) = {
    actionsView.setVisible(visible)
  }

  private def getActivationSummary(context: Context, regTime: Instant, regLocation: Option[Location]): String = {
    val now = LocalDateTime.now(ZoneId.systemDefault)
    val time = ZTimeFormatter.getSeparatorTime(context, now, LocalDateTime.ofInstant(regTime, ZoneId.systemDefault), DateFormat.is24HourFormat(context), ZoneId.systemDefault, false)
    context.getString(R.string.pref_devices_device_activation_summary, time, regLocation.fold("?")(_.getName))
  }



  fingerprintView.onClickEvent{ _ =>
    val clipboard: ClipboardManager = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip: ClipData = ClipData.newPlainText(getContext.getString(R.string.pref_devices_device_fingerprint_copy_description), fingerprint)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(getContext, R.string.pref_devices_device_fingerprint_copy_toast, Toast.LENGTH_SHORT).show()
  }

  override def setVerified(verified: Boolean) = {
    verifiedSwitch.setChecked(verified)
  }
}

case class DeviceDetailsBackStackKey(args: Bundle) extends BackStackKey(args) {
  import DeviceDetailsBackStackKey._

  val deviceId = ClientId(args.getString(DeviceIdKey, ""))
  var controller = Option.empty[DeviceDetailsViewController]

  override def nameId = R.string.pref_devices_device_screen_title

  override def layoutId = R.layout.preferences_device_details

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[DeviceDetailsViewImpl]).map(view => DeviceDetailsViewController(view, deviceId)(view.injector, view))
  }

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

case class DeviceDetailsViewController(view: DeviceDetailsView, clientId: ClientId)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  val client = for {
    zms <- zms
    client <- Signal.future(zms.otrClientsService.getClient(zms.selfUserId, clientId))
    fp <- zms.otrService.fingerprintSignal(zms.selfUserId, clientId).orElse(Signal(Some(Array())))//TODO: this isn't returning remote fingerprints for some reason...
  } yield (client, fp, zms.clientId == clientId)

  client.on(Threading.Ui){
    case (Some(client), fingerprint, self) =>
      view.setName(client.model)
      view.setId(client.id.str)
      view.setActivated(client.regTime.getOrElse(Instant.EPOCH), client.regLocation)
      fingerprint.foreach(fp => view.setFingerPrint(new String(fp)))
      view.setActionsVisible(!self)
    case _ =>
  }

}
