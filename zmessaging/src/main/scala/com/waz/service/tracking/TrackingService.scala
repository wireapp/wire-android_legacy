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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model._
import com.waz.service.call.Avs.AvsClosedReason
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState.{OtherCalling, SelfCalling, SelfConnected, SelfJoining}
import com.waz.service.tracking.ContributionEvent.fromMime
import com.waz.service.tracking.TrackingService.ZmsProvider
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.RichWireInstant
import com.waz.utils.events.{EventContext, EventStream, Signal}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Try

trait TrackingService {
  def events: EventStream[(Option[ZMessaging], TrackingEvent)]

  def track(event: TrackingEvent, userId: Option[UserId] = None): Future[Unit]

  def loggedOut(reason: String, userId: UserId): Future[Unit] =
    track(LoggedOutEvent(reason), Some(userId))

  def optIn(): Future[Unit] = track(OptInEvent)
  def optOut(): Future[Unit] = track(OptOutEvent)

  def contribution(action: ContributionEvent.Action): Future[Unit]
  def assetContribution(assetId: AssetId, userId: UserId): Future[Unit]

  def exception(e: Throwable, description: String, userId: Option[UserId] = None)(implicit tag: LogTag): Future[Unit]
  def crash(e: Throwable): Future[Unit]

  def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method): Future[Unit]
  def integrationRemoved(integrationId: IntegrationId): Future[Unit]
  def historyBackedUp(isSuccess: Boolean): Future[Unit]
  def historyRestored(isSuccess: Boolean): Future[Unit]

  def trackCallState(userId: UserId, callInfo: CallInfo, reason: Option[AvsClosedReason] = None): Future[Unit]
}

object TrackingService {

  type ZmsProvider = Option[UserId] => Future[Option[ZMessaging]]

  implicit val dispatcher = new SerialDispatchQueue(name = "TrackingService")
  private[waz] implicit val ec: EventContext = EventContext.Global

  def exception(e: Throwable, description: String, userId: Option[UserId] = None)(implicit tag: LogTag): Future[Unit] = {
    ZMessaging.globalModule.map(_.trackingService.exception(e, description, userId)(tag))
  }

  def track(event: TrackingEvent, userId: Option[UserId] = None): Future[Unit] =
    ZMessaging.globalModule.map(_.trackingService.track(event, userId))

  trait NoReporting { self: Throwable => }

}

class TrackingServiceImpl(curAccount: Signal[Option[UserId]], zmsProvider: ZmsProvider) extends TrackingService {
  import TrackingService._

  val events = EventStream[(Option[ZMessaging], TrackingEvent)]()

  override def track(event: TrackingEvent, userId: Option[UserId] = None): Future[Unit] =
    zmsProvider(userId).map(events ! _ -> event)

  private def current = curAccount.head.flatMap(zmsProvider)

  override def contribution(action: ContributionEvent.Action) = current.map {
    case Some(z) =>
      for {
        Some(convId) <- z.convsStats.selectedConversationId.head
        Some(conv)   <- z.convsStorage.get(convId)
        userIds      <- z.membersStorage.activeMembers(convId).head
        users        <- z.usersStorage.listAll(userIds.toSeq)
        isGroup      <- z.conversations.isGroupConversation(convId)
      } {
        events ! Option(z) -> ContributionEvent(action, isGroup, conv.ephemeralExpiration.map(_.duration), users.exists(_.isWireBot), !conv.isTeamOnly, conv.isMemberFromTeamGuest(z.teamId))
      }
    case _ => //
  }

  override def exception(e: Throwable, description: String, userId: Option[UserId] = None)(implicit tag: LogTag) = {
    val cause = rootCause(e)
    track(ExceptionEvent(cause.getClass.getSimpleName, details(cause), description, throwable = Some(e))(tag), userId)
  }

  override def crash(e: Throwable) = {
    val cause = rootCause(e)
    track(CrashEvent(cause.getClass.getSimpleName, details(cause), throwable = Some(e)))
  }

  @tailrec
  private def rootCause(e: Throwable): Throwable = Option(e.getCause) match {
    case Some(cause) => rootCause(cause)
    case None => e
  }

  private def details(rootCause: Throwable) =
    Try(rootCause.getStackTrace).toOption.filter(_.nonEmpty).map(_(0).toString).getOrElse("")

  override def assetContribution(assetId: AssetId, userId: UserId) = zmsProvider(Some(userId)).map {
    case Some(z) =>
      for {
        Some(msg)   <- z.messagesStorage.get(MessageId(assetId.str))
        Some(conv)  <- z.convsContent.convById(msg.convId)
        Some(asset) <- z.assetsStorage.get(assetId)
        userIds     <- z.membersStorage.activeMembers(conv.id).head
        users       <- z.usersStorage.listAll(userIds.toSeq)
        isGroup     <- z.conversations.isGroupConversation(conv.id)
      } yield track(ContributionEvent(fromMime(asset.mime), isGroup, msg.ephemeral, users.exists(_.isWireBot), !conv.isTeamOnly, conv.isMemberFromTeamGuest(z.teamId)), Some(userId))
    case _ => //
  }

  override def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method) = current.map {
    case Some(z) =>
      for {
        userIds <- z.membersStorage.activeMembers(convId).head
        users   <- z.usersStorage.listAll(userIds.toSeq)
        (bots, people) = users.partition(_.isWireBot)
      } yield track(IntegrationAdded(integrationId, people.size, bots.filterNot(_.integrationId.contains(integrationId)).size + 1, method))
    case None =>
  }

  def integrationRemoved(integrationId: IntegrationId) = track(IntegrationRemoved(integrationId))

  override def historyBackedUp(isSuccess: Boolean) =
    track(if (isSuccess) HistoryBackupSucceeded else HistoryBackupFailed)

  override def historyRestored(isSuccess: Boolean) =
    track(if (isSuccess) HistoryRestoreSucceeded else HistoryRestoreFailed)

  override def trackCallState(userId: UserId, callInfo: CallInfo, reason: Option[AvsClosedReason] = None) =
    ((callInfo.prevState, callInfo.state) match {
      case (None, Some(SelfCalling))      => Some("initiated")
      case (None, Some(OtherCalling))     => Some("received")
      case (Some(_), Some(SelfJoining))   => Some("joined")
      case (Some(_), Some(SelfConnected)) => Some("established")
      case (Some(_), None)                => Some("ended")
      case _ =>
        warn(s"Unexpected call state change: ${callInfo.prevState} => ${callInfo.state}, not tracking")
        None
    }).fold(Future.successful({})) { eventName =>

      val callEnded = callInfo.state.isEmpty && callInfo.prevState.isDefined

      for {
        Some(z)  <- zmsProvider(Some(userId))
        isGroup  <- z.conversations.isGroupConversation(callInfo.convId)
        memCount <- z.membersStorage.activeMembers(callInfo.convId).map(_.size).head
        withService <- z.conversations.isWithService(callInfo.convId)
        withGuests  <-
          if (isGroup)
            z.convsStorage.get(callInfo.convId).collect { case Some(conv) => !conv.isTeamOnly }.map(Some(_))
          else Future.successful(None)
        _ <-
          track(new CallingEvent(
            eventName,
            callInfo.startedAsVideoCall,
            isGroup,
            memCount,
            withService,
            callInfo.caller != z.selfUserId,
            withGuests,
            Option(callInfo.maxParticipants).filter(_ > 0),
            callInfo.estabTime.map(est => callInfo.joinedTime.getOrElse(est).until(est)),
            callInfo.endTime.map(end => callInfo.estabTime.getOrElse(end).until(end)),
            reason,
            if (callEnded) Some(callInfo.wasVideoToggled) else None
          ))
      } yield {}
    }
}

object TrackingServiceImpl {

  import com.waz.threading.Threading.Implicits.Background

  def apply(accountsService: AccountsService): TrackingServiceImpl =
    new TrackingServiceImpl(
      accountsService.activeAccountId,
      (userId: Option[UserId]) => userId.fold(Future.successful(Option.empty[ZMessaging]))(uId => accountsService.zmsInstances.head.map(_.find(_.selfUserId == uId))))
}

