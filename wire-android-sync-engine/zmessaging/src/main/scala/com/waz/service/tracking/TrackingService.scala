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

import com.waz.content.UserPreferences.AnalyticsEnabled
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.tracking.TrackingService.ZmsProvider
import com.waz.service.tracking.TrackingServiceImpl.{CountlyEventProperties, RichHashMap}
import com.waz.service.{AccountsService, MetaDataService, ZMessaging}
import com.wire.signals.SerialDispatchQueue
import com.waz.utils.{MathUtils, RichWireInstant}
import com.wire.signals.{EventContext, EventStream, Signal}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

trait TrackingService {
  def events: EventStream[(Option[ZMessaging], TrackingEvent)]

  def track(event: TrackingEvent, userId: Option[UserId] = None): Future[Unit]

  def loggedOut(reason: String, userId: UserId): Future[Unit] =
    track(LoggedOutEvent(reason), Some(userId))

  def optIn(): Future[Unit] = track(OptInEvent)
  def optOut(): Future[Unit] = track(OptOutEvent)

  def contribution(action: ContributionEvent.Action, zms: Option[UserId] = None): Future[Unit]

  def msgDecryptionFailed(convId: RConvId, userId: UserId): Future[Unit]
  def trackCallState(userId: UserId, callInfo: CallInfo): Future[Unit]

  def exception(e: Throwable, description: String, userId: Option[UserId] = None)(implicit tag: LogTag): Future[Unit]
  def crash(e: Throwable): Future[Unit]

  def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method): Future[Unit]
  def integrationRemoved(integrationId: IntegrationId): Future[Unit]
  def historyBackedUp(isSuccess: Boolean): Future[Unit]
  def historyRestored(isSuccess: Boolean): Future[Unit]


  def isTrackingEnabled: Signal[Boolean]
}

class DummyTrackingService extends TrackingService {
  override def events: EventStream[(Option[ZMessaging], TrackingEvent)] = EventStream()
  override def track(event: TrackingEvent, userId: Option[UserId]): Future[Unit] = Future.successful(())
  override def contribution(action: ContributionEvent.Action, zms: Option[UserId] = None): Future[Unit] = Future.successful(())
  override def msgDecryptionFailed(convId: RConvId, userId: UserId): Future[Unit] = Future.successful(())
  override def trackCallState(userId: UserId, callInfo: CallInfo): Future[Unit] = Future.successful(())
  override def exception(e: Throwable, description: String, userId: Option[UserId])(implicit tag: LogTag): Future[Unit] = Future.successful(())
  override def crash(e: Throwable): Future[Unit] = Future.successful(())
  override def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method): Future[Unit] = Future.successful(())
  override def integrationRemoved(integrationId: IntegrationId): Future[Unit] = Future.successful(())
  override def historyBackedUp(isSuccess: Boolean): Future[Unit] = Future.successful(())
  override def historyRestored(isSuccess: Boolean): Future[Unit] = Future.successful(())
  override def isTrackingEnabled: Signal[Boolean] = Signal.const(true)
}

object TrackingService {

  type ZmsProvider = Option[UserId] => Future[Option[ZMessaging]]

  implicit val dispatcher = new SerialDispatchQueue(name = "TrackingService")
  private[waz] implicit val ec: EventContext = EventContext.Global

  trait NoReporting { self: Throwable => }
}

class TrackingServiceImpl(curAccount: => Signal[Option[UserId]], zmsProvider: ZmsProvider, metaDataService: MetaDataService)
  extends TrackingService with DerivedLogTag {

  import TrackingService._

  override lazy val isTrackingEnabled: Signal[Boolean] =
    ZMessaging.currentAccounts.activeZms.flatMap {
      case Some(z) => z.userPrefs(AnalyticsEnabled).signal
      case _ => Signal.const(false)
    }

  val events = EventStream[(Option[ZMessaging], TrackingEvent)]()

  override def track(event: TrackingEvent, userId: Option[UserId] = None): Future[Unit] = isTrackingEnabled.head.flatMap {
    case true  => zmsProvider(userId).map(events ! _ -> event)
    case false => Future.successful(())
  }

  private def current = curAccount.head.flatMap(zmsProvider)

  private def getZmsOrElseCurrent(userId: Option[UserId]): Future[ZMessaging] =
    (if(userId.isDefined) zmsProvider(userId) else current)
      .collect { case Some(s) => s }

  private def getMainSegments(z: ZMessaging, convId: ConvId): Future[CountlyEventProperties] =
    getCommonSegments(z, convId).map(e => e ++ getUniversalSegments())

  private def getUniversalSegments(): CountlyEventProperties =
    mutable.Map[String, AnyRef]()
      .putSegment("app_name", "android")
      .putSegment("app_version", metaDataService.versionName.split('-').take(1).mkString)

  private def getCommonSegments(z: ZMessaging, convId: ConvId): Future[CountlyEventProperties] = {
    for {
      Some(convType) <- z.convsStorage.get(convId).map(_.map(_.convType.name()))
      userIds        <- z.membersStorage.activeMembers(convId).head
      users          <- z.usersStorage.listAll(userIds.toSeq)
      guests         =  users.filter(_.isGuest(z.teamId))
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

  override def contribution(action: ContributionEvent.Action, userId: Option[UserId] = None): Future[Unit] = isTrackingEnabled.head.flatMap {
    case true =>
      for {
        z <- getZmsOrElseCurrent(userId)
        Some(convId)   <- z.selectedConv.selectedConversationId.head
        segments <- getMainSegments(z, convId)
      } yield {
        segments += ("message_action" -> action.name)

        events ! Option(z) -> ContributionEvent(action, segments)
      }
    case false => Future.successful(())
  }

  override def msgDecryptionFailed(rConvId: RConvId, userId: UserId): Future[Unit] = isTrackingEnabled.head.flatMap {
    case true =>
      for {
        Some(z) <- zmsProvider(Some(userId))
        Some(convId) <- z.convsStorage.getByRemoteId(rConvId).map(_.map(_.id))
        segments <- getMainSegments(z, convId)
      } yield events ! Option(z) -> MessageDecryptionFailed(segments)
    case false => Future.successful(())
  }

  override def trackCallState(userId: UserId, info: CallInfo): Future[Unit] = isTrackingEnabled.head.flatMap {
    case true =>
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
    case false => Future.successful(())
  }

  private def trackScreenShare(userId: UserId, info: CallInfo): Future[Unit] =
    if(info.screenShareEnded.isDefined)
      isTrackingEnabled.head.flatMap {
        case true =>
          for {
            Some(z)  <- zmsProvider(Some(userId))
            segments <- getMainSegments(z, info.convId)
            (direction, duration) = info.screenShareEnded.get
          } yield {
            segments.putSegment("screen_share_direction", direction)
            segments.putSegment("screen_share_duration", MathUtils.roundToNearest5(duration))

            events ! Option(z) -> ScreenShareEvent(segments)
          }
        case false => Future.successful(())
      }
    else Future.successful(())

  override def exception(e: Throwable, description: String, userId: Option[UserId] = None)(implicit tag: LogTag) = isTrackingEnabled.head.flatMap {
    case true =>
      val cause = rootCause(e)
      track(ExceptionEvent(cause.getClass.getSimpleName, details(cause), description, throwable = Some(e))(tag), userId)
    case false => Future.successful(())
  }

  override def crash(e: Throwable) = isTrackingEnabled.head.flatMap {
    case true =>
      val cause = rootCause(e)
      track(CrashEvent(cause.getClass.getSimpleName, details(cause), throwable = Some(e)))
    case false => Future.successful(())
  }

  @tailrec
  private def rootCause(e: Throwable): Throwable = Option(e.getCause) match {
    case Some(cause) => rootCause(cause)
    case None        => e
  }

  private def details(rootCause: Throwable) =
    Try(rootCause.getStackTrace).toOption.filter(_.nonEmpty).map(_ (0).toString).getOrElse("")

  override def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method) = isTrackingEnabled.head.flatMap {
    case true => current.map {
      case Some(z) =>
        for {
          userIds        <- z.membersStorage.activeMembers(convId).head
          users          <- z.usersStorage.listAll(userIds.toSeq)
          (bots, people) =  users.partition(_.isWireBot)
        } yield track(IntegrationAdded(integrationId, people.size, bots.filterNot(_.integrationId.contains(integrationId)).size + 1, method))
      case None =>
    }
    case false => Future.successful(())
  }

  def integrationRemoved(integrationId: IntegrationId) = isTrackingEnabled.head.flatMap {
    case true  => track(IntegrationRemoved(integrationId))
    case false => Future.successful(())
  }

  override def historyBackedUp(isSuccess: Boolean) = isTrackingEnabled.head.flatMap {
    case true if isSuccess => track(HistoryBackupSucceeded)
    case true              => track(HistoryBackupFailed)
    case false             => Future.successful(())
  }

  override def historyRestored(isSuccess: Boolean) = isTrackingEnabled.head.flatMap {
    case true if isSuccess => track(HistoryRestoreSucceeded)
    case true              => track(HistoryRestoreFailed)
    case false             => Future.successful(())
  }

}

object TrackingServiceImpl extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def apply(accountsService: => AccountsService, metaDataService: MetaDataService): TrackingServiceImpl =
    new TrackingServiceImpl(
      accountsService.activeAccountId,
      (userId: Option[UserId]) => userId.fold(Future.successful(Option.empty[ZMessaging]))(uId => accountsService.zmsInstances.head.map(_.find(_.selfUserId == uId))),
      metaDataService
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

