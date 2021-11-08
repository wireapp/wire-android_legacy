package com.waz.services.fcm

import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.Preferences.Preference
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.Uid
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.clock
import com.waz.sync.client.PushNotificationsClient.LoadNotificationsResult
import com.waz.sync.client.{PushNotificationEncoded, PushNotificationsClient}
import com.waz.utils.{Backoff, ExponentialBackoff, _}
import com.waz.znet2.http.ResponseCode
import com.wire.signals.{CancellableFuture, Serialized}
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._

trait FCMPushHandler {
  def syncNotifications(): CancellableFuture[Unit]
}

final class FCMPushHandlerImpl(userPrefs:   UserPreferences,
                               globalPrefs: GlobalPreferences,
                               client:      PushNotificationsClient,
                               clientId:    ClientId)
  extends FCMPushHandler with DerivedLogTag {
  import FCMPushHandler._
  import com.waz.threading.Threading.Implicits.Background

  override def syncNotifications(): CancellableFuture[Unit] =
    Serialized.apply("syncInProgress")(syncHistory())

  private lazy val idPref: Preference[Option[Uid]] = userPrefs.preference(LastStableNotification)
  private lazy val beDriftPref: Preference[Duration] = globalPrefs.preference(BackendDrift)

  private def updateDrift(time: Option[Instant]) =
    CancellableFuture.lift(
      beDriftPref.mutate(v => time.fold(v)(clock.instant.until(_)))
    )

  private def syncHistory(): CancellableFuture[Unit] = {
    verbose(l"Sync history")
    for {
      lastId  <- CancellableFuture.lift(idPref())
      results <- lastId.map(load(_, 0)).getOrElse(CancellableFuture.successful(None))
    } yield results match {
      case Some(Results(nots, time)) if nots.nonEmpty =>
        updateDrift(time)
          .flatMap(_ => updateLastId(nots))
          .flatMap(_ => syncHistory())
      case Some(Results(_, time)) =>
        updateDrift(time).map(_ => None)
      case None =>
        CancellableFuture.successful(None)
    }
  }

  // TODO: change to storeNotifications in SQCORE-1138
  private def updateLastId(nots: Seq[PushNotificationEncoded]): CancellableFuture[Unit] =
    if (nots.nonEmpty) {
      verbose(l"FCM push notification: \n ${nots.map(_.events)}")
      val res = nots.lift(nots.lastIndexWhere(!_.transient))
      if (res.nonEmpty) CancellableFuture.lift(idPref := res.map(_.id)) else CancellableFuture.successful(())
    } else
      CancellableFuture.successful(())

  @inline
  private def futureHistoryResults(notifications: Vector[PushNotificationEncoded] = Vector.empty,
                                   time:          Option[Instant] = None): CancellableFuture[Option[Results]] =
    CancellableFuture.successful(Some(Results(notifications, time)))

  private def load(lastId: Uid, attempts: Int): CancellableFuture[Option[Results]] =
    client.loadNotifications(Some(lastId), clientId).flatMap {
      case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
        verbose(l"FCM (1) notifications: ${response.notifications}, time: ${response.beTime}")
        futureHistoryResults(response.notifications, response.beTime)
      case Right(LoadNotificationsResult(response, historyLost))
        if response.notifications.nonEmpty && response.hasMore && !historyLost =>
        load(response.notifications.last.id, 0).flatMap {
          case Some(results) =>
            verbose(l"FCM (2) notifications: ${response.notifications}, results: ${results.notifications}, time: ${response.beTime}, time2: ${results.time}")
            futureHistoryResults(
              response.notifications ++ results.notifications,
              if (results.time.isDefined) results.time else response.beTime
            )
          case None =>
            verbose(l"FCM (3) notifications: ${response.notifications}, time: ${response.beTime}, but returning None")
            CancellableFuture.successful(None)
        }
      case Right(LoadNotificationsResult(response, historyLost)) if historyLost =>
        warn(l"FCM /notifications failed with 404, history lost")
        // we don't handle historyLost here so we just try to recover with what we got
        futureHistoryResults(response.notifications, response.beTime)
      case Right(LoadNotificationsResult(response, _)) =>
        // here we know that there are no notifications in the response
        futureHistoryResults(time = response.beTime)
      case Left(e@ErrorResponse(ResponseCode.Unauthorized, _, _)) =>
        warn(l"FCM Logged out, failing sync request")
        CancellableFuture.failed(FetchFailedException(e))
      case Left(err) if attempts <= FCMPushHandler.MaxRetries =>
        warn(l"FCM Request failed due to $err: attempting to load last page (since id: $lastId) again? (attempts: $attempts)")
        CancellableFuture
          .delay(SyncHistoryBackoff.delay(attempts))
          .flatMap { _ => load(lastId, attempts + 1) }
      case Left(err) =>
        error(l"FCM Request failed due to $err", err)
        CancellableFuture.failed(FetchFailedException(err))
    }
}

object FCMPushHandler {
  val MaxRetries: Int = 3
  val SyncHistoryBackoff: Backoff = new ExponentialBackoff(3.second, 15.seconds)

  def apply(userPrefs:   UserPreferences,
            globalPrefs: GlobalPreferences,
            client:      PushNotificationsClient,
            clientId:    ClientId): FCMPushHandler =
    new FCMPushHandlerImpl(userPrefs, globalPrefs, client, clientId)

  final case class Results(notifications: Vector[PushNotificationEncoded], time: Option[Instant])
  final case class FetchFailedException(err: ErrorResponse) extends Exception(s"Failed to fetch notifications: ${err.message}")
}
