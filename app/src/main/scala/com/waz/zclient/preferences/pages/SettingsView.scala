/**
 * Wire
 * Copyright (C) 2020 Wire Swiss GmbH
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

import android.content.{Context, Intent}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient._
import com.waz.zclient.common.views.FlatWireButton
import com.waz.zclient.feature.settings.about.SettingsAboutActivity
import com.waz.zclient.feature.settings.support.SettingsSupportActivity
import com.waz.zclient.preferences.views.TextButton
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, IntentUtils, RichView, StringUtils, UiStorage, UserSignal}

trait SettingsView {

  val onInviteClick: EventStream[Unit]

  def setInviteButtonEnabled(enabled: Boolean): Unit
  def startInviteIntent(name: String, handle: String): Unit
  def setDevSettingsEnabled(enabled: Boolean): Unit
}

class SettingsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with SettingsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_settings_layout)

  val navigator = inject[BackStackNavigator]

  val accountButton = findById[TextButton](R.id.settings_account)
  val devicesButton = findById[TextButton](R.id.settings_devices)
  val optionsButton = findById[TextButton](R.id.settings_options)
  val advancedButton = findById[TextButton](R.id.settings_advanced)
  val supportButton = findById[TextButton](R.id.settings_support)
  val aboutButton = findById[TextButton](R.id.settings_about)
  val devButton = findById[TextButton](R.id.settings_dev)
  val avsButton = findById[TextButton](R.id.settings_avs)
  val inviteButton = findById[FlatWireButton](R.id.profile_invite)

  accountButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AccountBackStackKey()) }
  devicesButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(DevicesBackStackKey())}
  optionsButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(OptionsBackStackKey()) }
  advancedButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AdvancedBackStackKey()) }
  supportButton.onClickEvent.on(Threading.Ui) { _ =>
    if (BuildConfig.KOTLIN_SETTINGS) {
      context.startActivity(SettingsSupportActivity.newIntent(context))
    } else {
      navigator.goTo(SupportBackStackKey())
    }
  }
  aboutButton.onClickEvent.on(Threading.Ui) { _ =>
    if (BuildConfig.KOTLIN_SETTINGS) {
      context.startActivity(SettingsAboutActivity.newIntent(context))
    } else {
      navigator.goTo(AboutBackStackKey())
    }
  }
  devButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(DevSettingsBackStackKey()) }
  avsButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AvsBackStackKey()) }

  override val onInviteClick: EventStream[Unit] = inviteButton.onClickEvent.map(_ => ())
  inviteButton.setText(R.string.pref_invite_title)
  inviteButton.setGlyph(R.string.glyph__invite)

  override def startInviteIntent(name: String, handle: String) = {
    val sharingIntent =
      IntentUtils.getInviteIntent(
        context.getString(R.string.people_picker__invite__share_text__header, name),
        context.getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(handle)))
    context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.people_picker__invite__share_details_dialog)))
  }

  override def setInviteButtonEnabled(enabled: Boolean) = inviteButton.setVisible(enabled)

  override def setDevSettingsEnabled(enabled: Boolean) = {
    devButton.setVisible(enabled)
    avsButton.setVisible(enabled)
  }

}

case class SettingsBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_category_title

  override def layoutId = R.layout.preferences_settings

  var controller = Option.empty[SettingsViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[SettingsViewImpl]).map(sv => new SettingsViewController(sv)(sv.injector, sv))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class SettingsViewController(view: SettingsView)(implicit inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {

  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val selfInfo = for {
    z <- zms
    self <- UserSignal(z.selfUserId)
  } yield (self.name, self.handle.fold("")(_.string))

  val team = zms.flatMap(_.teams.selfTeam)

  view.onInviteClick.onUi { _ =>
    selfInfo.currentValue.foreach {
      case (name, handle) =>
        view.startInviteIntent(name, handle)
      case _ =>
    }
  }

  team.onUi { team =>
    view.setInviteButtonEnabled(team.isEmpty)
  }

  view.setDevSettingsEnabled(BuildConfig.DEVELOPER_FEATURES_ENABLED)
}

