package com.waz.services.fcm

import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.Preferences.Preference
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.clock
import com.waz.service.otr.{NotificationParser, OtrEventDecoder}
import com.waz.service.push.PushNotificationEventsStorage
import com.waz.sync.client.PushNotificationsClient.LoadNotificationsResult
import com.waz.sync.client.{PushNotificationEncoded, PushNotificationsClient}
import com.waz.utils.{Backoff, ExponentialBackoff, _}
import com.waz.zclient.notifications.controllers.MessageNotificationsController
import com.waz.znet2.http.ResponseCode
import com.wire.signals.{CancellableFuture, Serialized}
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FCMPushHandler {
  def syncNotifications(): Future[Unit]
}

final class FCMPushHandlerImpl(userId:      UserId,
                               clientId:    ClientId,
                               client:      PushNotificationsClient,
                               storage:     PushNotificationEventsStorage,
                               decoder:     OtrEventDecoder,
                               parser:      NotificationParser,
                               controller:  MessageNotificationsController,
                               globalPrefs: GlobalPreferences,
                               userPrefs:   UserPreferences)
                              (implicit ec: ExecutionContext)
  extends FCMPushHandler with DerivedLogTag {
  import FCMPushHandler._

  override def syncNotifications(): Future[Unit] =
    Serialized.future("syncNotifications")(syncHistory())

  private lazy val idPref: Preference[Option[Uid]] = userPrefs.preference(LastStableNotification)
  private lazy val beDriftPref: Preference[Duration] = globalPrefs.preference(BackendDrift)

  private def updateDrift(time: Option[Instant]) =
    time.fold(Future.successful(()))(t => (beDriftPref := clock.instant.until(t)))

  private def processNotifications(notifications: Vector[PushNotificationEncoded]): Future[Unit] = {
    verbose(l"processNotifications($notifications)")
    for {
      encrypted <- storage.saveAll(notifications)
      _         <- processEncryptedEvents(encrypted)
      decrypted <- storage.getDecryptedRows
      decoded   =  decrypted.flatMap(ev => decoder.decode(ev))
      _         =  verbose(l"decoded events (${decoded.size}): $decoded")
      parsed    <- parser.parse(decoded)
      _         =  verbose(l"parsed events (${parsed.size}): $parsed")
      _         <- if (parsed.nonEmpty) controller.onNotificationsChanged(userId, parsed) else Future.successful(())
      _         <- updateLastId(notifications)
    } yield ()
  }

  private def syncHistory(): Future[Unit] =
    for {
      lastId              <- idPref()
      results             <- lastId.map(load(_, 0).future).getOrElse(Future.successful(None))
      Results(nots, time) =  results.getOrElse(Results.Empty)
      _                   <- updateDrift(time)
      _                   <- if (nots.nonEmpty) processNotifications(nots) else Future.successful(())
                             // at the end of processing we check if no new notifications came in the meantime
      _                   <- if (nots.nonEmpty) syncHistory() else Future.successful(())
    } yield ()

  private def updateLastId(nots: Seq[PushNotificationEncoded]) =
    nots.reverse.find(!_.transient).map(_.id) match {
      case Some(notId) => idPref := Some(notId)
      case _           => Future.successful(())
    }

  private def processEncryptedEvents(events: Seq[PushNotificationEvent]) =
    Future.traverse(events) {
      case event @ GetOtrEvent(otrEvent) => decrypt(event.index, otrEvent)
      case event                         => storage.setAsDecrypted(event.index)
    }.map(_ => ())

  private def decrypt(index: Int, otrEvent: OtrEvent): Future[Unit] =
    decoder.decryptStoredOtrEvent(otrEvent, storage.writeClosure(index)).flatMap {
      case Left(Duplicate) =>
        verbose(l"Ignoring duplicate message")
        storage.remove(index)
      case Left(err) =>
        val e = OtrErrorEvent(otrEvent.convId, otrEvent.convDomain, otrEvent.time, otrEvent.from, otrEvent.fromDomain, err)
        error(l"Got error when decrypting: $e")
        storage.writeError(index, e)
      case Right(_) =>
        Future.successful(())
    }

  @inline
  private def futureHistoryResults(notifications: Vector[PushNotificationEncoded] = Vector.empty,
                                   time:          Option[Instant] = None) =
    CancellableFuture.successful(Some(Results(notifications, time)))

  private def load(lastId: Uid, attempts: Int): CancellableFuture[Option[Results]] =
    client.loadNotifications(Some(lastId), clientId).flatMap {
      case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
        futureHistoryResults(response.notifications, response.beTime)
      case Right(LoadNotificationsResult(response, historyLost))
        if response.notifications.nonEmpty && response.hasMore && !historyLost =>
        load(response.notifications.last.id, 0).flatMap {
          case Some(results) =>
            futureHistoryResults(
              response.notifications ++ results.notifications,
              if (results.time.isDefined) results.time else response.beTime
            )
          case None =>
            CancellableFuture.successful(None)
        }
      case Right(LoadNotificationsResult(response, historyLost)) if historyLost =>
        warn(l"/notifications failed with 404, history lost")
        // we don't handle historyLost here so we just try to recover with what we got
        futureHistoryResults(response.notifications, response.beTime)
      case Right(LoadNotificationsResult(response, _)) =>
        // here we know that there are no notifications in the response
        futureHistoryResults(time = response.beTime)
      case Left(e@ErrorResponse(ResponseCode.Unauthorized, _, _)) =>
        warn(l"Logged out, failing sync request")
        CancellableFuture.failed(FetchFailedException(e))
      case Left(err) if attempts <= FCMPushHandler.MaxRetries =>
        warn(l"Request failed due to $err: attempting to load last page (since id: $lastId) again? (attempts: $attempts)")
        CancellableFuture
          .delay(SyncHistoryBackoff.delay(attempts))
          .flatMap { _ => load(lastId, attempts + 1) }
      case Left(err) =>
        error(l"Request failed due to $err", err)
        CancellableFuture.failed(FetchFailedException(err))
    }
}

object FCMPushHandler {
  val MaxRetries: Int = 3
  val SyncHistoryBackoff: Backoff = new ExponentialBackoff(3.second, 15.seconds)

  def apply(userId:      UserId,
            clientId:    ClientId,
            client:      PushNotificationsClient,
            storage:     PushNotificationEventsStorage,
            decoder:     OtrEventDecoder,
            parser:      NotificationParser,
            controller:  MessageNotificationsController,
            globalPrefs: GlobalPreferences,
            userPrefs:   UserPreferences)
           (implicit ec: ExecutionContext): FCMPushHandler =
    new FCMPushHandlerImpl(userId, clientId, client, storage, decoder, parser, controller, globalPrefs, userPrefs)

  final case class Results(notifications: Vector[PushNotificationEncoded], time: Option[Instant])

  object Results {
    val Empty: Results = Results(Vector.empty, Option.empty)
  }

  final case class FetchFailedException(err: ErrorResponse) extends Exception(s"Failed to fetch notifications: ${err.message}")

  object GetOtrEvent extends DerivedLogTag {
    def unapply(event: PushNotificationEvent): Option[OtrEvent] =
      if (!event.event.isOtrMessageAdd) None
      else ConversationEvent.ConversationEventDecoder(event.event.toJson) match {
        case otrEvent: OtrEvent => Some(otrEvent)
        case _                  => error(l"Unrecognized event: ${event.event}"); None
      }
  }
}
