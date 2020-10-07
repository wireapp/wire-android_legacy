/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import android.content.Context
import android.net.Uri
import com.waz.log.LogSE._
import com.waz.content.Preferences.Preference.PrefCodec.IntensityLevelCodec
import com.waz.content.UserPreferences.Sounds
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.media.manager.config.Configuration
import com.waz.media.manager.context.IntensityLevel
import com.waz.media.manager.{MediaManager, MediaManagerListener}
import com.waz.utils._
import com.wire.signals._
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

trait MediaManagerService {
  def mediaManager:                Future[MediaManager]
  def soundIntensity:              Signal[IntensityLevel]
  def isSpeakerOn:                 Signal[Boolean]
  def setSpeaker(enable: Boolean): Future[Unit]
}

class DefaultMediaManagerService(context: Context) extends MediaManagerService with DerivedLogTag { self =>
  import com.waz.service.MediaManagerService._

  import com.waz.threading.Threading.Implicits.Background

  private val onPlaybackRouteChanged = EventStream[PlaybackRoute]()
  private val audioConfig = Try(new JSONObject(IoUtils.asString(context.getAssets.open(AudioConfigAsset)))).toOption

  val mediaManager = Future {
    val manager = MediaManager.getInstance(context.getApplicationContext)
    manager.addListener(new MediaManagerListener {
      override def mediaCategoryChanged(convId: String, category: Int): Int = category // we don't need to do anything in here, I guess, and the return value gets ignored anyway

      override def onPlaybackRouteChanged(route: Int): Unit = {
        val pbr = PlaybackRoute.fromAvsIndex(route)
        debug(l"onPlaybackRouteChanged($pbr)")
        self.onPlaybackRouteChanged ! pbr
      }
    })
    audioConfig.foreach(manager.registerMediaFromConfiguration)
    manager
  }

  mediaManager.onFailure {
    case NonFatal(e) =>
      error(l"MediaManager was not instantiated properly", e)
  }

  val isSpeakerOn = RefreshingSignal(mediaManager.map(_.isLoudSpeakerOn), onPlaybackRouteChanged)

  val soundIntensity = Option(ZMessaging.currentAccounts).map(_.activeZms.flatMap {
    case None => Signal.const(IntensityLevelCodec.default)
    case Some(z) => z.userPrefs.preference(Sounds).signal
  }).getOrElse {
    warn(l"No CurrentAccounts available - this may be being called too early...")
    Signal.const(IntensityLevelCodec.default)
  }

  soundIntensity { intensity =>
    mediaManager.foreach(_.setIntensity(intensity))
  }

  //TODO these are not used - what were/are they for?
  lazy val audioConfigUris =
    audioConfig.map(new Configuration(_).getSoundMap.asScala.mapValues { value =>
      val packageName = context.getPackageName
      Uri.parse(s"android.resource://$packageName/${context.getResources.getIdentifier(value.getPath, "raw", packageName)}")
    }).getOrElse(Map.empty[String, Uri])

  def getSoundUri(name: String): Option[Uri] = audioConfigUris.get(name)

  def setSpeaker(speaker: Boolean) = mediaManager.map { mm => if (speaker) mm.turnLoudSpeakerOn() else mm.turnLoudSpeakerOff() }

}

object MediaManagerService {
  val AudioConfigAsset = "android.json"
}
