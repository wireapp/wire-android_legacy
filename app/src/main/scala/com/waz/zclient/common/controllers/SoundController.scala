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
package com.waz.zclient.common.controllers

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.{Build, Vibrator}
import android.provider.Settings
import android.text.TextUtils
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.media.manager.MediaManager
import com.waz.media.manager.context.IntensityLevel
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RingtoneUtils.{getUriForRawId, isDefaultValue}
import com.waz.zclient.utils.{DeprecationUtils, RingtoneUtils}
import com.waz.zclient.{R, _}
import com.wire.signals.{EventContext, Signal}

trait SoundController {
  def currentTonePrefs: (String, String, String)

  def isVibrationEnabled: Boolean
  def soundIntensityNone: Boolean
  def soundIntensityFull: Boolean

  def setIncomingRingTonePlaying(userId: UserId, play: Boolean): Unit
  def setOutgoingRingTonePlaying(play: Boolean, isVideo: Boolean = false): Unit

  def playCallEstablishedSound(userId: UserId): Unit
  def playCallEndedSound(userId: UserId): Unit
  def playCallDroppedSound(): Unit
  def playAlert(): Unit
  def shortVibrate(): Unit
  def playMessageIncomingSound(firstMessage: Boolean): Unit
  def playPingFromThem(): Unit
  def playPingFromMe(): Unit
  def playCameraShutterSound(): Unit
  def playRingFromThemInCall(play: Boolean): Unit
}

class SoundControllerImpl(implicit inj: Injector, cxt: Context)
  extends SoundController with Injectable with DerivedLogTag {

  private implicit val ev = EventContext.Implicits.global
  private implicit val ec = Threading.Background

  private val zms                 = inject[Signal[ZMessaging]]
  private val audioManager        = Option(inject[AudioManager])
  private val vibrator            = Option(inject[Vibrator])
  private val notificationManager = inject[NotificationManager]
  private val globalPrefs         = inject[GlobalPreferences]
  private val ZenModeKey          = "zen_mode"

  private val mediaManager   = zms.flatMap(z => Signal.from(z.mediamanager.mediaManager))
  private val soundIntensity = zms.flatMap(_.mediamanager.soundIntensity)

  private var _mediaManager = Option.empty[MediaManager]
  mediaManager.foreach(m => _mediaManager = Some(m))

  def currentTonePrefs: (String, String, String) = tonePrefs.currentValue.getOrElse((null, null, null))

  private def shouldPlayCallTone: Boolean = if (Build.VERSION.SDK_INT >= 23) {
    matchCallToneSettings(notificationManager.getCurrentInterruptionFilter, shouldPrioritise = true)
  } else {
    matchCallToneSettings(Settings.Global.getInt(cxt.getContentResolver, ZenModeKey))
  }

  private def matchCallToneSettings(callToneSetting: Int, shouldPrioritise: Boolean = false): Boolean =
    callToneSetting match {
      case NotificationManager.INTERRUPTION_FILTER_ALL                            => true
      case NotificationManager.INTERRUPTION_FILTER_PRIORITY if (shouldPrioritise) =>
        val policy = notificationManager.getNotificationPolicy
        (policy.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY
          || policy.priorityCategories == NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
          || policy.priorityCategories == NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS)
      case NotificationManager.INTERRUPTION_FILTER_PRIORITY                       => true
      case NotificationManager.INTERRUPTION_FILTER_UNKNOWN                        => true
      case NotificationManager.INTERRUPTION_FILTER_ALARMS                         => false
      case NotificationManager.INTERRUPTION_FILTER_NONE                           => true
      case _                                                                      => true
    }

  private val tonePrefs = (for {
    ringTone <- globalPrefs.preference(GlobalPreferences.RingTone).signal
    textTone <- globalPrefs.preference(GlobalPreferences.TextTone).signal
    pingTone <- globalPrefs.preference(GlobalPreferences.PingTone).signal
  } yield (ringTone, textTone, pingTone)).disableAutowiring()

  tonePrefs.foreach {
    case (ring, text, ping) => setCustomSoundUrisFromPreferences(ring, text, ping)
  }

  override def isVibrationEnabled: Boolean =
    globalPrefs.preference(GlobalPreferences.VibrateEnabled).signal.currentValue.getOrElse(false)

  override def soundIntensityNone: Boolean =
    soundIntensity.currentValue.contains(IntensityLevel.NONE)

  override def soundIntensityFull: Boolean =
    soundIntensity.currentValue.isEmpty || soundIntensity.currentValue.contains(IntensityLevel.FULL)

  override def setIncomingRingTonePlaying(userId: UserId, play: Boolean): Unit = {
    if (!soundIntensityNone && shouldPlayCallTone) setMediaPlaying(R.raw.ringing_from_them, play)
    setVibrating(R.array.ringing_from_them, play, loop = true, Some(userId))
  }

  //no vibration needed here
  //TODO - there seems to be a race condition somewhere, where this method is called while isVideo is incorrect
  //This leads to the case where one of the media files starts playing, and we never receive the stop for it. Always ensuring
  //that both files stops is a fix for the symptom, but not the root cause - which could be affecting other things...
  override def setOutgoingRingTonePlaying(play: Boolean, isVideo: Boolean = false): Unit =
    if (play) {
      if (soundIntensityFull) setMediaPlaying(if (isVideo) R.raw.ringing_from_me_video else R.raw.ringing_from_me)
    } else {
      setMediaPlaying(R.raw.ringing_from_me_video, play = false)
      setMediaPlaying(R.raw.ringing_from_me, play = false)
    }

  override def playCallEstablishedSound(userId: UserId): Unit = {
    if (soundIntensityFull) setMediaPlaying(R.raw.ready_to_talk)
    setVibrating(R.array.ready_to_talk, userId = Some(userId))
  }

  override def playCallEndedSound(userId: UserId): Unit = {
    if (soundIntensityFull) setMediaPlaying(R.raw.talk_later)
    setVibrating(R.array.talk_later, userId = Some(userId))
  }

  override def playCallDroppedSound(): Unit = {
    if (soundIntensityFull) setMediaPlaying(R.raw.call_drop)
    setVibrating(R.array.call_dropped)
  }

  override def playAlert(): Unit = {
    if (soundIntensityFull) setMediaPlaying(R.raw.alert)
    setVibrating(R.array.alert)
  }

  def shortVibrate(): Unit =
    setVibrating(R.array.alert)

  def playMessageIncomingSound(firstMessage: Boolean): Unit = {
    if (firstMessage && !soundIntensityNone) setMediaPlaying(R.raw.first_message)
    else if (soundIntensityFull) setMediaPlaying(R.raw.new_message)
    setVibrating(R.array.new_message)
  }

  def playPingFromThem(): Unit = {
    if (!soundIntensityNone) setMediaPlaying(R.raw.ping_from_them)
    setVibrating(R.array.ping_from_them)
  }

  //no vibration needed
  def playPingFromMe(): Unit =
    if (!soundIntensityNone) setMediaPlaying(R.raw.ping_from_me)

  def playCameraShutterSound(): Unit = {
    if (soundIntensityFull) setMediaPlaying(R.raw.camera)
    setVibrating(R.array.camera)
  }

  def playRingFromThemInCall(play: Boolean): Unit =
    if (shouldPlayCallTone) setMediaPlaying(R.raw.ringing_from_them_incall, play)
  /**
    * @param play For looping patterns, this parameter will tell to stop vibrating if they have previously been started
    */
  private def setVibrating(patternId: Int, play: Boolean = true, loop: Boolean = false, userId: Option[UserId] = None): Unit = {
    (audioManager, vibrator) match {
      case (Some(am), Some(vib)) if play &&
                                    am.getRingerMode != AudioManager.RINGER_MODE_SILENT &&
                                    isVibrationEnabled =>
        vib.cancel() // cancel any current vibrations
        DeprecationUtils.vibrate(vib, getIntArray(patternId).map(_.toLong), if (loop) 0 else -1)
      case (_, Some(vib)) => vib.cancel()
      case _ =>
    }
  }

  /**
    * @param play For media that play for a long time (or continuously??) this parameter will stop them
    */
  private def setMediaPlaying(resourceId: Int, play: Boolean = true) = _mediaManager.foreach { mm =>
    val resName = getResEntryName(resourceId)
    verbose(l"setMediaPlaying: ${redactedString(resName)}, play: $play")
    if (play) mm.playMedia(resName) else mm.stopMedia(resName)
  }

  /**
    * Takes a saved "URL" from the apps shared preferences, and uses that to set the different sounds in the app.
    * There are several "groups" of sounds, each with their own uri. There is then also a given "mainId" for each group,
    * which gets set first, and is then used to determine if the uri points to the "default" sound file.
    *
    * Then for the other ids related to that group, they are all set to either the default, or whatever new uri is specified
    */
  def setCustomSoundUrisFromPreferences(ringTonePref: String, textTonePref: String, pingTonePref: String): Unit = {
    setCustomSoundUrisFromPreferences(ringTonePref, R.raw.ringing_from_them, Seq(R.raw.ringing_from_me, R.raw.ringing_from_me_video, R.raw.ringing_from_them_incall))
    setCustomSoundUrisFromPreferences(pingTonePref, R.raw.ping_from_them,    Seq(R.raw.ping_from_me))
    setCustomSoundUrisFromPreferences(textTonePref, R.raw.new_message,       Seq(R.raw.first_message, R.raw.new_message_gcm))
  }

  private def setCustomSoundUrisFromPreferences(uri: String, mainId: Int, otherIds: Seq[Int]): Unit = {
    val isDefault = TextUtils.isEmpty(uri) || isDefaultValue(cxt, uri, R.raw.ringing_from_them)
    val finalUri  = if (isDefault) getUriForRawId(cxt, R.raw.ringing_from_them).toString
                    else if(RingtoneUtils.isSilent(uri)) ""
                    else uri

    setCustomSoundUri(mainId, finalUri)
    otherIds.foreach(id => setCustomSoundUri(id, if (isDefault) getUriForRawId(cxt, id).toString else finalUri))
  }

  private def setCustomSoundUri(resourceId: Int, uri: String): Unit = {
    try {
      _mediaManager.foreach { mm =>
        if (TextUtils.isEmpty(uri)) mm.unregisterMedia(getResEntryName(resourceId))
        else mm.registerMediaFileUrl(getResEntryName(resourceId), Uri.parse(uri))
      }
    }
    catch {
      case e: Exception => error(l"Could not set custom uri: ${redactedString(uri)}", e)
    }
  }
}
