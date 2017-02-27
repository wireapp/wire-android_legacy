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
package com.waz.zclient.tracking

import java.util

import android.content.{Context, Intent}
import android.net.ConnectivityManager
import android.os.Bundle
import android.telephony.TelephonyManager
import com.localytics.android.Localytics
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message.Type._
import com.waz.api.impl.ErrorResponse
import com.waz.api.{EphemeralExpiration, NetworkMode}
import com.waz.content.UsersStorage
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{UserId, _}
import com.waz.service.downloads.DownloadRequest.WireAssetRequest
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.tracking.events.calling.EndedCallAVSMetricsEvent
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.core.controllers.tracking
import com.waz.zclient.core.controllers.tracking.attributes.{Attribute, RangedAttribute}
import com.waz.zclient.core.controllers.tracking.events.AVSMetricEvent
import com.waz.zclient.core.controllers.tracking.events.media.{CompletedMediaActionEvent, SentPictureEvent}
import com.waz.zclient.core.controllers.tracking.events.onboarding.GeneratedUsernameEvent
import com.waz.zclient._
import org.threeten.bp.{Duration, Instant}
import com.waz.utils._
import com.waz.utils.returning
import com.waz.zclient.controllers.tracking.events.launch.AppLaunch
import com.waz.zclient.controllers.tracking.screens.{ApplicationScreen, RegistrationScreen}
import com.waz.zclient.utils.ContextUtils._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.language.{implicitConversions, postfixOps}

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable {
  import GlobalTrackingController._
  import Threading.Implicits.Background

  val zMessaging = inject[Signal[ZMessaging]]
  val zmsOpt = inject[Signal[Option[ZMessaging]]]

  private var currentZms = Option.empty[ZMessaging]
  private var appLaunchedTracked = false
  private var sentEvents = Set.empty[String]

  zmsOpt {
    case zms if currentZms != zms =>
      currentZms = zms
      zms.foreach(trackingEventListeners)
    case _ => //already registered to this zms, do nothing.
  }

  def tagEvent(event: tracking.events.Event) = {
    verbose(s"Tag event=[name='${event.getName}',\nattributes='${event.getAttributes}',\nrangedAttributes='${event.getRangedAttributes}']")("TrackingController")
    setCustomDims() map { _ =>
      val eventAttributes = new util.HashMap[String, String]
      import scala.collection.JavaConversions._
      for (attribute <- event.getRangedAttributes.keySet) {
        val eventCount = event.getRangedAttributes.get(attribute)
        eventAttributes.put(attribute.name, createRangedAttribute(eventCount, attribute.rangeSteps))
        eventAttributes.put(attribute.actualValueName, Integer.toString(eventCount))
      }

      for (attribute <- event.getAttributes.keySet) {
        eventAttributes.put(attribute.name, event.getAttributes.get(attribute))
      }

      if (isTrackingEnabled) Localytics.tagEvent(event.getName, eventAttributes)
    }

  }

  def tagAVSMetricEvent(event: AVSMetricEvent) = {
    verbose(s"tagAVSMetricEvent: ${event.getName}, \n ${event.getAttributes}")
    setCustomDims() map { _ =>
      if (isTrackingEnabled) Localytics.tagEvent(event.getName, event.getAttributes)
    }
  }

  def appLaunched(intent: Intent): Unit = {
    if (!appLaunchedTracked) {
      val event: AppLaunch = new AppLaunch(intent)
      tagEvent(event)
      appLaunchedTracked = true
    }
  }

  def loadFromSavedInstance(savedInstanceState: Bundle) =
    Option(savedInstanceState).flatMap(st => Option(st.getStringArray(SAVED_STATE_SENT_TAGS))).foreach { tags =>
      sentEvents = tags.toSet
    }

  def saveToSavedInstance(outState: Bundle) = {
    outState.putStringArray(SAVED_STATE_SENT_TAGS, sentEvents.toArray)
  }

  def onRegistrationScreen(screen: RegistrationScreen) = {
    verbose(s"Tag registration screen=[name='${Option(screen)}']")
    Option(screen).map(_.toString).filter(!sentEvents.contains(_) && isTrackingEnabled).foreach { sc =>
      sentEvents += sc
      Localytics.tagScreen(sc)
    }
  }

  def onApplicationScreen(screen: ApplicationScreen) = {
    verbose(s"Tag application screen=[\nname='${Option(screen)}']")
    Option(screen).map(_.toString).filter(_ => isTrackingEnabled).foreach(Localytics.tagScreen)
  }

  private def isTrackingEnabled = {
    val pref = ZMessaging.currentGlobal.prefs.uiPreferences.getBoolean(getString(R.string.pref_advanced_analytics_enabled_key), true)
    pref && !BuildConfig.DISABLE_TRACKING_KEEP_LOGGING
  }

  private def setCustomDims(): Future[Unit] = loadCustomDimensions.map { attrs =>
    val dims = getCustomDimensions(attrs, cxt)
    //TODO print ALL custom dimensions, in a more type-safe(?) way...
    verbose(
      s""" Custom dimensions:
          | Day:        ${dims.head}
          | Hour:       ${dims(1)}
          | Contacts:   ${dims(2)}
          | Groups:     ${dims(3)}
          | Color:      ${dims(4)}
          | AB Testing: ${dims(5)}
      """.stripMargin)

    if (isTrackingEnabled) dims.zipWithIndex.foreach { case (value, i) => Localytics.setCustomDimension(i, value) }
  }

  /**
    * Register tracking event listeners on SE services in this method. We need a method here, since whenever the signal
    * zms fires, we want to discard the previous reference to the subscriber. Not doing so will cause this class to keep
    * reference to old instances of the services under zms (?)
    */
  def trackingEventListeners(zms: ZMessaging) = {
    zms.handlesSync.responseSignal.onChanged { data =>
      tagEvent(new GeneratedUsernameEvent(data.exists(_.nonEmpty)))
    }

    import AssetEvent._
    def mediaType(mime: Mime) = {
      import com.waz.zclient.core.controllers.tracking.attributes.CompletedMediaType._
      mime match {
        case Mime.Image() => PHOTO
        case Mime.Audio() => AUDIO
        case Mime.Video() => VIDEO
        case _            => FILE
      }
    }

    val downloader  = zms.assetDownloader
    val messages    = zms.messagesStorage
    val assets      = zms.assetsStorage
    val convsUI     = zms.convsUi
    val handlesSync = zms.handlesSync

    convsUI.assetUploadStarted.map(_.id) { assetTrackingData(_).map {
      case AssetTrackingData(convType, withOtto, isEphemeral, exp, assetSize, m) =>
        tagEvent(new CompletedMediaActionEvent(mediaType(m), convType.toString, withOtto, isEphemeral, expToString(isEphemeral, exp)))
        tagEvent(initiatedFileUploadEvent(m, assetSize, convType, isEphemeral, exp))

        if (Mime.Image.unapply(m))
          tagEvent(new SentPictureEvent(SentPictureEvent.Source.CLIP, convType.toString, SentPictureEvent.Method.DEFAULT, SentPictureEvent.SketchSource.NONE, withOtto, isEphemeral, expToString(isEphemeral, exp)))

    }}

    messages.onMessageSent {
      case msg if msg.isAssetMessage => assetTrackingData(msg.assetId).map {
        case AssetTrackingData(convType, withOtto, isEphemeral, exp, assetSize, mime) =>
          val duration = Duration.between(msg.localTime, Instant.now)
          tagEvent(successfullyUploadedFileEvent(mime, assetSize, duration))
      }
      case _ =>
    }

    convsUI.assetUploadCancelled { mime => tagEvent(cancelledFileUploadEvent(mime))}

    assets.onUploadFailed.filter(_.status == AssetStatus.UploadCancelled).map(_.mime)(m => tagEvent(cancelledFileUploadEvent(m)))

    downloader.onDownloadStarting {
      case WireAssetRequest(_, id, _, _, _, _) => assetTrackingData(id).map {
        case AssetTrackingData(convType, withOtto, isEphemeral, exp, assetSize, mime) =>
          tagEvent(initiatedFileDownloadEvent(mime, assetSize))
      }
      case _ => // ignore
    }

    downloader.onDownloadDone {
      case WireAssetRequest(_, id, _, _, _, _) => assetTrackingData(id).map {
        case AssetTrackingData(convType, withOtto, isEphemeral, exp, assetSize, mime) =>
          tagEvent(successfullyDownloadedFileEvent(mime, assetSize))
      }
      case _ => // ignore
    }

    downloader.onDownloadFailed {
      case (WireAssetRequest(_, id, _, _, _, _), err) if err.code != ErrorResponse.CancelledCode => assetTrackingData(id).map {
        case AssetTrackingData(convType, withOtto, isEphemeral, exp, assetSize, mime) =>
          tagEvent(failedFileDownloadEvent(mime))
      }
      case _ => // ignore
    }

    convsUI.assetUploadFailed { error =>
      tagEvent(FailedFileUploadEvent(error))
    }

    messages.onMessageFailed {
      case (msg, error) if msg.isAssetMessage =>
        tagEvent(FailedFileUploadEvent(error))
      case _ =>
    }

    zms.flowmanager.onAvsMetricsReceived { avsMetrics =>
      zms.users.withSelfUserFuture { selfUser =>
        zms.convsContent.convByRemoteId(avsMetrics.rConvId).flatMap {
          case Some(conv) =>
            zms.voice.getVoiceChannel(conv.id, selfUser).map { ch =>
              ch.data.tracking.joined.fold() { instant =>

                verbose(s"AVS metrics: $avsMetrics")
                // TODO: Separate video call when avsMetrics.isVideoCall() works
                /*
                if (avsMetrics.isVideoCall()) {
                    trackingController.tagAVSMetricEvent(new EndedVideoCallAVSMetricsEvent(avsMetrics));
                } else {
                    trackingController.tagAVSMetricEvent(new EndedCallAVSMetricsEvent(avsMetrics));
                }
                */

                avsMetrics.isVideoCall = ch.data.video.isVideoCall
                avsMetrics.kindOfCall = ch.data.tracking.kindOfCall
                tagAVSMetricEvent(new EndedCallAVSMetricsEvent(avsMetrics))
              }
            }
          case None => Future.successful(())
        }
      }
    }
  }

  //-1 is the default value for non-logged in users (when zms is not defined)
  private def loadCustomDimensions: Future[CustomAttributes] = {
    import ConversationType._
    for {
      zms    <- zmsOpt.head
      self   =  zms.fold(UserId.Zero)(_.selfUserId)
      convs  <- zms.fold(Future.successful(Seq.empty[ConversationData]))(_.convsStorage.list)
      users  <- zms.fold(Future.successful(Seq.empty[UserData]))(_.usersStorage.list)
      voice  <- zms.fold(Future.successful(-1))(_.callLog.numberOfEstablishedVoiceCalls)
      video  <- zms.fold(Future.successful(-1))(_.callLog.numberOfEstablishedVideoCalls)
      texts  <- zms.fold(Future.successful(-1))(_.messagesStorage.countSentByType(self, TEXT))
      rich   <- zms.fold(Future.successful(-1))(_.messagesStorage.countSentByType(self, RICH_MEDIA))
      images <- zms.fold(Future.successful(-1))(_.messagesStorage.countSentByType(self, ASSET))
    } yield {

      val (groups, archived, muted, contacts, blocked) = if (self == UserId.Zero) (-1, -1, -1, -1, -1)
      else {
        convs.iterator.map { c =>
          ((c.convType == Group)?, c.archived?, c.muted?, (c.convType == OneToOne && !c.hidden)?, (c.convType == OneToOne && c.hidden)?)
        }.foldLeft((0, 0, 0, 0, 0)) { case ((accG, accA, accM, accC, accB), (g, a, m, c, b)) =>
          (accG + g, accA + a, accM + m, accC + c, accB + b)
        }
      }

      val autoConnected = users.iterator.map(u => (u.isAutoConnect && ! u.isWireBot)?).sum

      CustomAttributes(groups, archived, muted, contacts, blocked, autoConnected, voice, video, texts + rich, images)
    }
  }.logFailure(reportHockey = true)

  private def assetTrackingData(id: AssetId): Future[AssetTrackingData] = {
    for {
      zms         <- zMessaging.head
      Some(msg)   <- zms.messagesStorage.get(MessageId(id.str))
      Some(conv)  <- zms.convsContent.convById(msg.convId)
      Some(asset) <- zms.assetsStorage.get(id)
      withOtto    <- isOtto(conv, zms.usersStorage)
    } yield AssetTrackingData(conv.convType, withOtto, msg.isEphemeral, msg.ephemeral, asset.size, asset.mime)
  }

}

object GlobalTrackingController {
  val SAVED_STATE_SENT_TAGS = "SAVED_STATE_SENT_TAGS"

  private implicit class RichBoolean(val b: Boolean) extends AnyVal {
    def ? : Int = if (b) 1 else 0
  }

  //implicit converter from Scala tracking event to Java tracking event for compatibility with older tracking code
  implicit def toJava(event: Event): com.waz.zclient.core.controllers.tracking.events.Event = new com.waz.zclient.core.controllers.tracking.events.Event {
    override def getName: String = event.name

    override def getRangedAttributes: util.Map[RangedAttribute, Integer] = {
      returning(new util.HashMap[RangedAttribute, Integer]()) { attrs =>
        event.rangedAttributes.foreach { case (ra, int) =>
          attrs.put(ra, Integer.valueOf(int))
        }
      }
    }

    override def getAttributes: util.Map[Attribute, String] = event.attributes.asJava
  }

  def isOtto(conv: ConversationData, users: UsersStorage): Future[Boolean] =
    if (conv.convType == ConversationType.OneToOne) users.get(UserId(conv.id.str)).map(_.exists(_.isWireBot))(Threading.Background)
    else successful(false)

  case class AssetTrackingData(conversationType: ConversationType, withOtto: Boolean, isEphemeral: Boolean, expiration: EphemeralExpiration, assetSize: Long, mime:Mime)

  case class CustomAttributes(groups:        Int,
                              archived:      Int,
                              muted:         Int,
                              contacts:      Int,
                              blocked:       Int,
                              autoConnected: Int,
                              voiceCalls:    Int,
                              videoCalls:    Int,
                              textsSent:     Int,
                              imagesSent:    Int)

  protected def getCustomDimensions(attrs: CustomAttributes, context: Context): List[String] = {

    import RangedAttribute._

    val preferences = context.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    val networkInfo = connectivityManager.getActiveNetworkInfo

    val mode = NetworkModeService.computeMode(networkInfo, telephonyManager) match {
      case NetworkMode._2G  => "2G"
      case NetworkMode.EDGE => "EDGE"
      case NetworkMode._3G  => "3G"
      case NetworkMode._4G  => "4G"
      case NetworkMode.WIFI => "WIFI"
      case _ => "unknown"
    }

    List(
      attrs.autoConnected -> NUMBER_OF_CONTACTS,
      attrs.contacts      -> NUMBER_OF_CONTACTS,
      attrs.groups        -> NUMBER_OF_GROUP_CONVERSATIONS,
      attrs.voiceCalls    -> NUMBER_OF_VOICE_CALLS,
      attrs.videoCalls    -> NUMBER_OF_VIDEO_CALLS,
      attrs.textsSent     -> TEXT_MESSAGES_SENT,
      attrs.imagesSent    -> IMAGES_SENT
    ).map {case (count, attr) => createRangedAttribute(count, attr.rangeSteps)} ++ List(
      Integer.toString(preferences.getInt(UserPreferencesController.USER_PERFS_AB_TESTING_GROUP, 0)),
      mode
    )
  }

  /**
    * This part (the method createRangedAttribute) of the Wire software uses source code from the beats2 project.
    * (https://code.google.com/p/beats2/source/browse/trunk/beats/src/com/localytics/android/LocalyticsSession.java#810)
    *
    * Copyright (c) 2009, Char Software, Inc. d/b/a Localytics
    * All rights reserved.
    *
    * Redistribution and use in source and binary forms, with or without
    * modification, are permitted provided that the following conditions are met:
    * - Redistributions of source code must retain the above copyright
    * notice, this list of conditions and the following disclaimer.
    * - Neither the name of Char Software, Inc., Localytics nor the names of its
    * contributors may be used to endorse or promote products derived from this
    * software without specific prior written permission.
    *
    * THIS SOFTWARE IS PROVIDED BY CHAR SOFTWARE, INC. D/B/A LOCALYTICS ''AS IS'' AND
    * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    * DISCLAIMED. IN NO EVENT SHALL CHAR SOFTWARE, INC. D/B/A LOCALYTICS BE LIABLE
    * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
    * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
    * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
    * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
    * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
    *
    * Sorts an int value into a predefined, pre-sorted set of intervals, returning a string representing the new expected value.
    * The array must be sorted in ascending order, with the first element representing the inclusive lower bound and the last
    * element representing the exclusive upper bound. For instance, the array [0,1,3,10] will provide the following buckets: less
    * than 0, 0, 1-2, 3-9, 10 or greater.
    *
    * @param actualValue The int value to be bucketed.
    * @param steps       The sorted int array representing the bucketing intervals.
    * @return String representation of { @code actualValue} that has been bucketed into the range provided by { @code steps}.
    */
  def createRangedAttribute(actualValue: Int, steps: Array[Int]): String = {
    if (actualValue < steps(0)) "less than " + steps(0)
    else if (actualValue >= steps(steps.length - 1)) steps(steps.length - 1) + " and above"
    else {
      var bucketIndex = util.Arrays.binarySearch(steps, actualValue)
      if (bucketIndex < 0) {
        bucketIndex = (-bucketIndex) - 2
      }
      if (steps(bucketIndex) == (steps(bucketIndex + 1) - 1)) {
        Integer.toString(steps(bucketIndex))
      }
      else {
        steps(bucketIndex) + "-" + (steps(bucketIndex + 1) - 1)
      }
    }
  }
}
