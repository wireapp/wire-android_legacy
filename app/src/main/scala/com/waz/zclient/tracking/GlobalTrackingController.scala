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

import com.waz.ZLog._
import com.waz.api.EphemeralExpiration
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{UserId, _}
import com.waz.service.ZMessaging
import com.waz.service.downloads.DownloadRequest.WireAssetRequest
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.controllers.tracking.ITrackingController
import com.waz.zclient.controllers.tracking.events.calling.EndedCallAVSMetricsEvent
import com.waz.zclient.core.controllers.tracking.attributes.{Attribute, RangedAttribute}
import com.waz.zclient.core.controllers.tracking.events.media.{CompletedMediaActionEvent, SentPictureEvent}
import com.waz.zclient.core.controllers.tracking.events.onboarding.GeneratedUsernameEvent
import com.waz.zclient.{Injectable, Injector, WireContext}
import org.threeten.bp.{Duration, Instant}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.language.implicitConversions

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable {
  import GlobalTrackingController._
  import Threading.Implicits.Background

  val zMessaging = inject[Signal[ZMessaging]]
  val zmsOpt = inject[Signal[Option[ZMessaging]]]
  //TODO steadily shift methods from ITrackingController to here..
  val legacyController = inject[ITrackingController]

  import legacyController._

  private var currentZms = Option.empty[ZMessaging]

  zmsOpt {
    case zms if currentZms != zms =>
      currentZms = zms
      zms.foreach(trackingEventListeners)
    case _ => //already registered to this zms, do nothing.
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
}
