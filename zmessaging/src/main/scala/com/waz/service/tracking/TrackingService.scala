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
package com.waz.service.tracking

import com.waz.content.UserPreferences.TrackingEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.tracking.TrackingService.ZmsProvider
import com.waz.service.tracking.TrackingServiceImpl.{CountlyEventProperties, RichHashMap}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.{MathUtils, RichWireInstant}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.implicitConversions

trait TrackingService {
  def events: EventStream[(Option[ZMessaging], TrackingEvent)]

  def contribution(action: ContributionEvent.Action, zms: Option[UserId] = None): Future[Unit]
  def msgDecryptionFailed(convId: RConvId, userId: UserId): Future[Unit]
  def trackCallState(userId: UserId, callInfo: CallInfo): Future[Unit]
  def appOpen(userId: UserId): Future[Unit]

  def isTrackingEnabled: Signal[Boolean]

  def onTrackingIdChange: SourceStream[TrackingId]
}

class DummyTrackingService extends TrackingService {
  override def events: EventStream[(Option[ZMessaging], TrackingEvent)] = EventStream()
  override def contribution(action: ContributionEvent.Action, zms: Option[UserId] = None): Future[Unit] = Future.successful(())
  override def msgDecryptionFailed(convId: RConvId, userId: UserId): Future[Unit] = Future.successful(())
  override def trackCallState(userId: UserId, callInfo: CallInfo): Future[Unit] = Future.successful(())
  override def appOpen(userId: UserId): Future[Unit] = Future.successful(())
  override def isTrackingEnabled: Signal[Boolean] = Signal.const(true)
  override def onTrackingIdChange: SourceStream[TrackingId] = EventStream()
}

object TrackingService {
  type ZmsProvider = Option[UserId] => Future[Option[ZMessaging]]
}

class TrackingServiceImpl(curAccount: => Signal[Option[UserId]], zmsProvider: ZmsProvider, versionName: String)
  extends TrackingService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override lazy val isTrackingEnabled: Signal[Boolean] =
    ZMessaging.currentAccounts.activeZms.flatMap {
      case Some(z) => z.userPrefs(TrackingEnabled).signal
      case _       => Signal.const(false)
    }

  private def withTracking(action: => Future[Unit]): Future[Unit] = isTrackingEnabled.head.flatMap {
    case true  => action.recover { case _ => Future.successful(()) }
    case false => Future.successful(())
  }

  val events = EventStream[(Option[ZMessaging], TrackingEvent)]()

  private def getZmsOrElseCurrent(userId: Option[UserId]): Future[Option[ZMessaging]] =
    if(userId.isDefined) zmsProvider(userId)
    else curAccount.head.flatMap(zmsProvider)

  private def getMainSegments(z: ZMessaging, convId: ConvId): Future[CountlyEventProperties] =
    getCommonSegments(z, convId).map(e => e ++ getUniversalSegments())

  private def getUniversalSegments(): CountlyEventProperties = {
    mutable.Map[String, AnyRef]()
      .putSegment("app_name", "android")
      .putSegment("app_version", versionName.split('-').take(1).mkString)
  }

  private def getCommonSegments(z: ZMessaging, convId: ConvId): Future[CountlyEventProperties] = {
    for {
      Some(convType) <- z.convsStorage.get(convId).map(_.map(_.convType.name()))
      userIds        <- z.membersStorage.activeMembers(convId).head
      users          <- z.usersStorage.listAll(userIds.toSeq)
      guests         =  users.filter(_.isGuest(z.teamId, Some(z.selfDomain)))
      proGuestCount  =  guests.count(_.teamId.isDefined)
      wirelessGuests =  guests.count(_.expiresAt.isDefined)
      servicesCount  =  users.count(_.isWireBot)
    } yield {
      mutable.Map[String, AnyRef]()
        .putSegment("conversation_type", convType.toLowerCase)
        .putSegment("conversation_size", MathUtils.logRoundFactor6(userIds.size))
        .putSegment("conversation_guests", MathUtils.logRoundFactor6(guests.size))
        .putSegment("conversation_guest_pro", MathUtils.logRoundFactor6(proGuestCount))
        .putSegment("conversation_guests_wireless", MathUtils.logRoundFactor6(wirelessGuests))
        .putSegment("conversation_services", MathUtils.logRoundFactor6(servicesCount))
    }
  }

  private def getCallEndedSegments(info: CallInfo): CountlyEventProperties = {
    def calcDuration(info: CallInfo): Long =
      MathUtils.roundToNearest5(
        info.endTime.map(end => info.estabTime.getOrElse(end).until(end).getSeconds).get)

    mutable.Map[String, AnyRef]()
      .putSegment("call_duration", calcDuration(info))
      .putSegment("call_participants", info.maxParticipants)
      .putSegment("call_AV_switch_toggle", info.wasVideoToggled)
      .putSegment("call_screen_share", info.wasScreenShareUsed)
      .putSegment("call_end_reason", info.endReason.get)
  }

  override def contribution(action: ContributionEvent.Action, userId: Option[UserId] = None): Future[Unit] = withTracking {
    for {
      Some(z)      <- getZmsOrElseCurrent(userId)
      Some(convId) <- z.selectedConv.selectedConversationId.head
      segments     <- getMainSegments(z, convId)
    } yield {
      segments += ("message_action" -> action.name)
      events ! Option(z) -> ContributionEvent(action, segments)
    }
  }

  override def msgDecryptionFailed(rConvId: RConvId, userId: UserId): Future[Unit] = withTracking {
    for {
      Some(z)      <- zmsProvider(Some(userId))
      Some(convId) <- z.convsStorage.getByRemoteId(rConvId).map(_.map(_.id))
      segments     <- getMainSegments(z, convId)
    } yield events ! Option(z) -> MessageDecryptionFailedEvent(segments)
  }

  override def trackCallState(userId: UserId, info: CallInfo): Future[Unit] = withTracking {
    ((info.prevState, info.state) match {
      case (None, SelfCalling)      => Some("initiated")
      case (None, OtherCalling)     => Some("received")
      case (Some(_), SelfJoining)   => Some("joined")
      case (Some(_), SelfConnected) => Some("established")
      case (Some(_), Ended)         => Some("ended")
      case _ =>
        warn(l"Unexpected call state change: ${info.prevState} => ${info.state}, not tracking")
        None
    }).fold(Future.successful(())) { eventName =>
      (for {
        Some(z)           <- zmsProvider(Some(userId))
        segments          <- getMainSegments(z, info.convId)
        callDirection     = if(info.caller != z.selfUserId) "incoming" else "outgoing"
        callVideo         = info.videoSendState == VideoState.Started || info.videoSendState == VideoState.BadConnection
        callEndedSegments = if(eventName.equals("ended")) getCallEndedSegments(info) else mutable.Map.empty
      } yield {
        segments ++= callEndedSegments += ("call_video" -> callVideo.toString)

        if(!(eventName.equals("initiated") || eventName.equals("received")))
          segments += ("call_direction" -> callDirection.toString)

        events ! Option(z) -> CallingEvent(eventName, segments)
        trackScreenShare(userId, info)
      }).flatMap { _ =>
        //if we have accepted a call, we need to track this separately as a contribution
        import ContributionEvent.Action._
        if(info.prevState.contains(OtherCalling) && info.state == SelfJoining) {
          val eventType = if (info.isVideoCall) VideoCall else AudioCall
          contribution(eventType, Some(userId))
        } else Future.successful(())
      }
    }
  }

  private def trackScreenShare(userId: UserId, info: CallInfo): Future[Unit] =
    if(info.screenShareEnded.isDefined) withTracking {
      for {
        Some(z)  <- zmsProvider(Some(userId))
        segments <- getMainSegments(z, info.convId)
        (direction, duration) = info.screenShareEnded.get
      } yield {
        segments.putSegment("screen_share_direction", direction)
        segments.putSegment("screen_share_duration", MathUtils.roundToNearest5(duration))

        events ! Option(z) -> ScreenShareEvent(segments)
      }
    }
    else Future.successful(())

  override def appOpen(userId: UserId): Future[Unit] = withTracking {
    for {
      Some(z)  <- zmsProvider(Some(userId))
    } yield events ! Option(z) -> AppOpenEvent(getUniversalSegments())
  }

  override val onTrackingIdChange: SourceStream[TrackingId] = EventStream()
}

object TrackingServiceImpl extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def apply(accountsService: => AccountsService, versionName: String): TrackingServiceImpl =
    new TrackingServiceImpl(
      accountsService.activeAccountId,
      (userId: Option[UserId]) => userId.fold(Future.successful(Option.empty[ZMessaging]))(uId => accountsService.zmsInstances.head.map(_.find(_.selfUserId == uId))),
      versionName
    )

  type CountlyEventProperties = mutable.Map[String, AnyRef]

  implicit class RichHashMap(v: CountlyEventProperties) {
    def putSegment(key: String, value: String): CountlyEventProperties =
      v += (key -> value.asInstanceOf[AnyRef])

    def putSegment(key: String, value: Int): CountlyEventProperties =
      v + (key -> value.asInstanceOf[AnyRef])

    def putSegment(key: String, value: Boolean): CountlyEventProperties =
      v + (key -> value.asInstanceOf[AnyRef])

    def putSegment(key: String, value: Double): CountlyEventProperties =
      v + (key -> value.asInstanceOf[AnyRef])
  }

}

