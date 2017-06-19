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
import android.widget.LinearLayout
import com.waz.content.GlobalPreferences
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient._
import com.waz.zclient.pages.main.profile.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.utils.ViewState


trait OptionsView {
  val onVbrSwitch: EventStream[Boolean]
  val onVibrationSwitch: EventStream[Boolean]
  val onDarkThemeSwitch: EventStream[Boolean]
  val onSendButtonSwitch: EventStream[Boolean]

  def setVbr(active: Boolean): Unit
  def setVibration(active: Boolean): Unit
  def setDarkTheme(active: Boolean): Unit
  def setSendButton(active: Boolean): Unit

  def setSounds(string: String): Unit
  def setRingtone(string: String): Unit
  def setTextTone(string: String): Unit
  def setPingTone(string: String): Unit
  def setDownloadPictures(string: String): Unit
}

class OptionsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with OptionsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_options_layout)

  val contactsSwitch    = findById[SwitchPreference](R.id.preferences_contacts)
  val vbrSwitch         = findById[SwitchPreference](R.id.preferences_vbr)
  val vibrationSwitch   = findById[SwitchPreference](R.id.preferences_vibration)
  val darkThemeSwitch   = findById[SwitchPreference](R.id.preferences_dark_theme)
  val sendButtonSwitch  = findById[SwitchPreference](R.id.preferences_send_button)
  val soundsButton      = findById[TextButton](R.id.preferences_sounds)

  override val onVbrSwitch        = vbrSwitch.onCheckedChange
  override val onVibrationSwitch  = vibrationSwitch.onCheckedChange
  override val onDarkThemeSwitch  = darkThemeSwitch.onCheckedChange
  override val onSendButtonSwitch = sendButtonSwitch.onCheckedChange

  contactsSwitch.setPreference(GlobalPreferences.ShareContacts)

  override def setVbr(active: Boolean) = vbrSwitch.setChecked(active)

  override def setVibration(active: Boolean) = vibrationSwitch.setChecked(active)

  override def setDarkTheme(active: Boolean) = darkThemeSwitch.setChecked(active)

  override def setSendButton(active: Boolean) = sendButtonSwitch.setChecked(active)

  override def setSounds(string: String) = soundsButton.setSubtitle(string)

  override def setRingtone(string: String) = {}

  override def setTextTone(string: String) = {}

  override def setPingTone(string: String) = {}

  override def setDownloadPictures(string: String) = {}
}

case class OptionsViewState() extends ViewState {
  override def name = "Options"//TODO: Resource

  override def layoutId = R.layout.preferences_options

  var controller = Option.empty[OptionsViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[OptionsViewImpl]).map(ov => new OptionsViewController(ov)(ov.injector, ov))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class OptionsViewController(view: OptionsView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  val prefs = zms.map(_.prefs)

  //TODO: avoid injection context... get theme from the preferences as well
  val context = inject[WireContext]
  view.setDarkTheme(context.asInstanceOf[PreferencesActivity].getControllerFactory.getThemeController.isDarkTheme)
  view.onDarkThemeSwitch{ value => context.asInstanceOf[PreferencesActivity].getControllerFactory.getThemeController.toggleThemePending(true)}

  prefs.flatMap(_.preference(GlobalPreferences.DownloadImages).signal).on(Threading.Ui){ view.setDownloadPictures }
}
