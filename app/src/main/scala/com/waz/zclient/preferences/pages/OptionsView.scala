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

import android.content.{Context, Intent}
import android.media.RingtoneManager
import android.net.Uri
import android.os.{Build, Bundle}
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.content.UserPreferences._
import com.waz.media.manager.context.IntensityLevel
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.preferences.dialogs.SoundLevelDialog
import com.waz.zclient.utils.{BackStackKey, RichView, RingtoneUtils}
import OptionsView._
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.waz.model.UserId
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.AndroidNotificationsManager
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.content.GlobalPreferences.{AppLockEnabled, IncognitoKeyboardEnabled}

trait OptionsView {
  def setSounds(level: IntensityLevel): Unit
  def setRingtone(string: String): Unit
  def setTextTone(string: String): Unit
  def setPingTone(string: String): Unit
  def setDownloadPictures(wifiOnly: Boolean): Unit
  def setAccountId(userId: UserId): Unit
}

object OptionsView {
  val RingToneResultId = 0
  val TextToneResultId = 1
  val PingToneResultId = 2
}

class OptionsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with OptionsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  implicit val ec = Threading.Ui
  protected lazy val zms = inject[Signal[ZMessaging]]

  inflate(R.layout.preferences_options_layout)

  val vbrSwitch               = findById[SwitchPreference](R.id.preferences_vbr)
  val vibrationSwitch         = findById[SwitchPreference](R.id.preferences_vibration)
  val darkThemeSwitch         = findById[SwitchPreference](R.id.preferences_dark_theme)
  val sendButtonSwitch        = findById[SwitchPreference](R.id.preferences_send_button)
  val appLockSwitch           = findById[SwitchPreference](R.id.preferences_app_lock)
  val soundsButton            = findById[TextButton](R.id.preferences_sounds)
  val downloadImagesSwitch    = findById[SwitchPreference](R.id.preferences_options_image_download)
  val hideScreenContentSwitch = findById[SwitchPreference](R.id.preferences_hide_screen)
  val messagePreviewSwitch = findById[SwitchPreference](R.id.preferences_message_previews)
  val incognitoKeyboardSwitch = findById[SwitchPreference](R.id.preferences_incognito_keyboard)

  val ringToneButton         = findById[TextButton](R.id.preference_sounds_ringtone)
  val textToneButton         = findById[TextButton](R.id.preference_sounds_text)
  val pingToneButton         = findById[TextButton](R.id.preference_sounds_ping)

  private lazy val defaultRingToneUri = RingtoneUtils.getUriForRawId(context, R.raw.ringing_from_them)
  private lazy val defaultTextToneUri = RingtoneUtils.getUriForRawId(context, R.raw.new_message)
  private lazy val defaultPingToneUri = RingtoneUtils.getUriForRawId(context, R.raw.ping_from_them)

  private var ringToneUri = ""
  private var textToneUri = ""
  private var pingToneUri = ""
  private var soundLevel = IntensityLevel.NONE
  private var accountId = UserId()

  darkThemeSwitch.setPreference(DarkTheme)
  downloadImagesSwitch.setPreference(DownloadImagesAlways)
  hideScreenContentSwitch.setPreference(HideScreenContent)
  vbrSwitch.setPreference(VBREnabled)
  messagePreviewSwitch.setPreference(MessagePreview)
  vibrationSwitch.setPreference(VibrateEnabled)
  sendButtonSwitch.setPreference(SendButtonEnabled)

  if (BuildConfig.FORCE_APP_LOCK) {
    appLockSwitch.setVisible(false)
  } else {
    appLockSwitch.setPreference(AppLockEnabled, global = true)
    appLockSwitch.setSubtitle(getString(R.string.pref_options_app_lock_summary, BuildConfig.APP_LOCK_TIMEOUT.toString))
    appLockSwitch.setVisible(true)
  }

  incognitoKeyboardSwitch.setPreference(IncognitoKeyboardEnabled, global = true)
  if (BuildConfig.FORCE_PRIVATE_KEYBOARD) {
    incognitoKeyboardSwitch.setVisibility(View.GONE)
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    soundsButton.setVisible(false)
    vibrationSwitch.setTitle(getString(R.string.pref_options_vibration_title_o))
  }

  if (BuildConfig.FORCE_HIDE_SCREEN_CONTENT) {
    hideScreenContentSwitch.setVisible(false)
    hideScreenContentSwitch.pref.foreach(_ := true)
  }

  private def openNotificationSettings(channelId: String) = {
    val intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext.getPackageName)
    intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    context.asInstanceOf[BaseActivity].startActivityForResult(intent, 0)
  }

  ringToneButton.onClickEvent{ _ => showRingtonePicker(RingtoneManager.TYPE_RINGTONE, defaultRingToneUri, RingToneResultId, ringToneUri)}
  textToneButton.onClickEvent{ _ =>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      openNotificationSettings(NotificationManagerWrapper.MessageNotificationsChannelId(accountId))
    } else {
      showRingtonePicker(RingtoneManager.TYPE_NOTIFICATION, defaultTextToneUri, TextToneResultId, textToneUri)
    }
  }
  pingToneButton.onClickEvent{ _ =>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      openNotificationSettings(NotificationManagerWrapper.PingNotificationsChannelId(accountId))
    } else {
      showRingtonePicker(RingtoneManager.TYPE_NOTIFICATION, defaultPingToneUri, PingToneResultId, pingToneUri)
    }
  }
  soundsButton.onClickEvent{ _ => showPrefDialog(SoundLevelDialog(soundLevel), SoundLevelDialog.Tag)}

  override def setSounds(level: IntensityLevel) = {
    soundLevel = level
    val string = soundLevel match {
      case IntensityLevel.NONE => context.getString(R.string.pref_options_sounds_none)
      case IntensityLevel.SOME => context.getString(R.string.pref_options_sounds_new_conversations_and_talks_only)
      case IntensityLevel.FULL => context.getString(R.string.pref_options_sounds_all)
    }
    soundsButton.setSubtitle(string)
  }

  override def setRingtone(uri: String) = {
    ringToneUri = uri
    setToneSubtitle(ringToneButton, defaultRingToneUri, uri)
  }

  override def setTextTone(uri: String) = {
    textToneUri = uri
    setToneSubtitle(textToneButton, defaultTextToneUri, uri)
  }

  override def setPingTone(uri: String) = {
    pingToneUri = uri
    setToneSubtitle(pingToneButton, defaultPingToneUri, uri)
  }

  private def setToneSubtitle(button: TextButton, defaultUri: Uri, uri: String): Unit = {
    val title =
      if (RingtoneUtils.isSilent(uri))
      context.getString(R.string.pref_options_ringtones_silent)
    else if (uri.isEmpty || uri.equals(defaultUri.toString))
      context.getString(R.string.pref_options_ringtones_default_summary)
    else
      RingtoneManager.getRingtone(context, Uri.parse(uri)).getTitle(context)
    button.setSubtitle(title)
  }

  override def setDownloadPictures(wifiOnly: Boolean) = {
    val names = getResources.getStringArray(R.array.pref_options_image_download_entries).toSeq
    downloadImagesSwitch.setSubtitle(if (wifiOnly) names.head else names.last)
  }

  private def showPrefDialog(f: Fragment, tag: String) = {
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }

  private def showRingtonePicker(ringtoneType: Int, defaultUri: Uri, resultId: Int, selectedUri: String): Unit = {
    val intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
    val uri =
      if(RingtoneUtils.isSilent(selectedUri))
        null
      else if (TextUtils.isEmpty(selectedUri) || defaultUri.toString.equals(selectedUri))
        Settings.System.DEFAULT_RINGTONE_URI
      else
        Uri.parse(selectedUri)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
    context.asInstanceOf[PreferencesActivity].startActivityForResult(intent, resultId)
  }

  override def setAccountId(userId: UserId): Unit = accountId = userId
}

case class OptionsBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_options_screen_title

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
  val userPrefs = zms.map(_.userPrefs)
  val team = zms.flatMap(_.teams.selfTeam)
  val notificationManagerWrapper = inject[NotificationManagerWrapper] match {
    case nmw: AndroidNotificationsManager => Some(nmw)
    case _ => None
  }

  private def getChannelTone(channelId: UserId => String) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      for {
        uId <- zms.map(_.selfUserId)
        _ <- inject[UiLifeCycle].uiActive // This forces updating in case the user changes the tone in the system channel
      } yield notificationManagerWrapper.flatMap(nm => Option(nm.getNotificationChannel(channelId(uId)).getSound))
    } else Signal.const(Option.empty[Uri])


  private val channelPingTone = getChannelTone(NotificationManagerWrapper.PingNotificationsChannelId)
  private val channelTextTone = getChannelTone(NotificationManagerWrapper.MessageNotificationsChannelId)

  userPrefs.flatMap(_.preference(DownloadImagesAlways).signal).onUi{ view.setDownloadPictures }
  userPrefs.flatMap(_.preference(Sounds).signal).onUi{ view.setSounds }
  userPrefs.flatMap(_.preference(RingTone).signal).onUi{ view.setRingtone }

  Signal(userPrefs.flatMap(_.preference(TextTone).signal), channelTextTone).map {
    case (_, Some(uri)) if Build.VERSION.SDK_INT >= Build.VERSION_CODES.O => uri.toString
    case (uri, _) => uri
  }.onUi(view.setTextTone)

  Signal(userPrefs.flatMap(_.preference(PingTone).signal), channelPingTone).map {
    case (_, Some(uri)) if Build.VERSION.SDK_INT >= Build.VERSION_CODES.O => uri.toString
    case (uri, _) => uri
  }.onUi(view.setPingTone)


  zms.onUi(z => view.setAccountId(z.selfUserId))
}
