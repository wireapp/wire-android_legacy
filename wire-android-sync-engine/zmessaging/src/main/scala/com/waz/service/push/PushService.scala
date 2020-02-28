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
package com.waz.service.push

import android.content.Context
import com.waz.api.NetworkMode.{OFFLINE, UNKNOWN}
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.Preferences.Preference
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.log.LogShow
import com.waz.model.Event.EventDecoder
import com.waz.model.FCMNotification.{Fetched, FinishedPipeline, StartedPipeline}
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.{accountTag, clock}
import com.waz.service._
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService.SyncMode
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.PushNotificationsClient.LoadNotificationsResult
import com.waz.sync.client.{PushNotificationEncoded, PushNotificationsClient}
import com.waz.threading.CancellableFuture.lift
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.events.{Signal, _}
import com.waz.utils.{RichInstant, _}
import com.waz.znet2.http.ResponseCode
import org.json.JSONObject
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/** PushService handles notifications coming from FCM, WebSocket, and fetch.
  * We assume FCM notifications are unreliable, so we use them only as information that we should perform a fetch (syncHistory).
  * A notification from the web socket may trigger a fetch on error. When fetching we ask BE to send us all notifications since
  * a given lastId - it may take time and we even may find out that we lost some history (lastId not found) which triggers slow sync.
  * So, it may happen that during the fetch new notifications will arrive through the web socket. Their processing should be
  * performed only after the fetch is done. For that we use a serial dispatch queue and we process notifications in futures,
  * which are put at the end of the queue, essentially making PushService synchronous.
  * Also, we need to handle fetch failures. If a fetch fails, we want to repeat it - after some time, or on network change.
  * In such case, new web socket notifications waiting to be processed should be dismissed. The new fetch will (if successful)
  * receive them in the right order.
  */

trait PushService {
  def syncNotifications(syncMode: SyncMode): Future[Unit]

  def onHistoryLost: SourceSignal[Instant] with BgEventSource
  def processing: Signal[Boolean]
  def waitProcessing: Future[Unit]

  /**
    * Drift to the BE time at the moment we fetch notifications
    * Used for calling (time critical) messages that can't always rely on local time, since the drift can be
    * greater than the window in which we need to respond to messages
    */
  def beDrift: Signal[Duration]
}

class PushServiceImpl(selfUserId:           UserId,
                      context:              Context,
                      userPrefs:            UserPreferences,
                      prefs:                GlobalPreferences,
                      notificationStorage:  PushNotificationEventsStorage,
                      client:               PushNotificationsClient,
                      clientId:             ClientId,
                      pipeline:             EventPipeline,
                      otrService:           OtrService,
                      wsPushService:        WSPushService,
                      accounts:             AccountsService,
                      pushTokenService:     PushTokenService,
                      network:              NetworkModeService,
                      lifeCycle:            UiLifeCycle,
                      tracking:             TrackingService,
                      sync:                 SyncServiceHandle,
                      timeouts:             Timeouts,
                      fcmService:           FCMNotificationStatsService)
                     (implicit ev: AccountContext) extends PushService { self =>
  import PushService._

  implicit val logTag: LogTag = accountTag[PushServiceImpl](selfUserId)
  private implicit val dispatcher = new SerialDispatchQueue(name = "PushService")

  override val onHistoryLost = new SourceSignal[Instant] with BgEventSource
  override val processing = Signal(false)

  override def waitProcessing =
    processing.filter(_ == false).head.map(_ => {})

  private val beDriftPref: Preference[Duration] = prefs.preference(BackendDrift)
  override val beDrift = beDriftPref.signal.disableAutowiring()

  private def updateDrift(time: Option[Instant]) = beDriftPref.mutate(v => time.fold(v)(clock.instant.until(_)))

  private lazy val idPref: Preference[Option[Uid]] = userPrefs.preference(LastStableNotification)

  notificationStorage.registerEventHandler { () =>
    Serialized.future(PipelineKey) {
      for {
        _ <- Future.successful(processing ! true)
        t =  System.currentTimeMillis()
        _ <- processEncryptedRows()
        _ <- processDecryptedRows()
        _ = verbose(l"events processing finished, time: ${System.currentTimeMillis() - t}ms")
        _ <- Future.successful(processing ! false)
      } yield {}
    }.recover {
      case ex =>
        processing ! false
        error(l"Unable to process events: $ex")
    }
  }

  private def processEncryptedRows(): Future[Unit] =
    notificationStorage.encryptedEvents.flatMap { rows =>
      verbose(l"Processing ${rows.size} encrypted rows")
      Future.sequence(rows.map { row =>
        if (!isOtrEventJson(row.event)) notificationStorage.setAsDecrypted(row.index)
        else {
          val otrEvent = ConversationEvent.ConversationEventDecoder(row.event).asInstanceOf[OtrEvent]
          val writer = notificationStorage.writeClosure(row.index)
          otrService.decryptStoredOtrEvent(otrEvent, writer).flatMap {
            case Left(Duplicate) =>
              verbose(l"Ignoring duplicate message")
              notificationStorage.remove(row.index)
            case Left(error) =>
              val e = OtrErrorEvent(otrEvent.convId, otrEvent.time, otrEvent.from, error)
              verbose(l"Got error when decrypting: $e")
              notificationStorage.writeError(row.index, e)
            case Right(_) => Future.successful(())
          }
        }
      }).map(_ => ())
    }

  private def processDecryptedRows(): Future[Unit] = {
    def decodeRow(event: PushNotificationEvent) =
      if(event.plain.isDefined && isOtrEventJson(event.event)) {
        verbose(l"decodeRow($event) for an otr event")
        val msg = GenericMessage(event.plain.get)
        val msgEvent = ConversationEvent.ConversationEventDecoder(event.event)
        returning(otrService.parseGenericMessage(msgEvent.asInstanceOf[OtrMessageEvent], msg)) { event =>
          verbose(l"decoded otr event: $event")
        }
      } else {
        verbose(l"decodeRow($event) for a non-otr event")
        Some(EventDecoder(event.event))
      }

    notificationStorage.getDecryptedRows().flatMap { rows =>
      verbose(l"Processing ${rows.size} rows")
      if (rows.nonEmpty) {
        val ids = rows.map(_.pushId).toSet
        for {
          _ <- fcmService.markNotificationsWithState(ids, StartedPipeline)
          _ <- pipeline(rows.flatMap(decodeRow))
          _ =  verbose(l"pipeline work finished")
          _ <- notificationStorage.removeRows(rows.map(_.index))
          _ =  verbose(l"rows removed from the notification storage")
          _ <- fcmService.markNotificationsWithState(ids, FinishedPipeline)
          _ =  verbose(l"notifications marked")
          _ <- processDecryptedRows()
          _ =  verbose(l"decrypted rows processed")
        } yield {}
      } else Future.successful(())
    }
  }

  private val timeOffset = System.currentTimeMillis()
  @inline private def timePassed = System.currentTimeMillis() - timeOffset

  wsPushService.notifications { nots =>
    syncNotifications(StoreNotifications(nots))
  }

  wsPushService.connected.onChanged.map(WebSocketChange).on(dispatcher){
    case source@WebSocketChange(true) =>
      verbose(l"sync history due to web socket change")
      syncNotifications(SyncHistory(source))
    case _ =>
  }

  private var fetchInProgress: Future[Unit] = Future.successful(())

  override def syncNotifications(syncMode: SyncMode): Future[Unit] = {
    def fetch(syncMode: SyncMode) = syncMode match {
      case StoreNotifications(notifications) => storeNotifications(notifications)
      case SyncHistory(source, withRetries)  => syncHistory(source, withRetries)
    }

    fetchInProgress = if (!fetchInProgress.isCompleted) fetchInProgress.flatMap(_ => fetch(syncMode)) else fetch(syncMode)
    fetchInProgress
  }

  private def storeNotifications(nots: Seq[PushNotificationEncoded]): Future[Unit] =
    if (nots.nonEmpty) {
      for {
        _   <- fcmService.markNotificationsWithState(nots.map(_.id).toSet, Fetched)
        _   <- notificationStorage.saveAll(nots)
        res =  nots.lift(nots.lastIndexWhere(!_.transient))
        _   <- if (res.nonEmpty) idPref := res.map(_.id) else Future.successful(())
      } yield None
    } else Future.successful(None)

  private def isOtrEventJson(ev: JSONObject) =
    ev.getString("type").equals("conversation.otr-message-add")

  private def futureHistoryResults(notifications: Vector[PushNotificationEncoded] = Vector.empty,
                                   time:          Option[Instant] = None,
                                   firstSync:     Boolean = false,
                                   historyLost:   Boolean = false): Future[Option[Results]] =
    Future.successful(Some(Results(notifications, time, firstSync, historyLost)))

  private def load(lastId: Option[Uid], firstSync: Boolean = false, attempts: Int = 0, withRetries: Boolean = true): Future[Option[Results]] =
    (lastId match {
      case None if firstSync => client.loadLastNotification(clientId)
      case id                => client.loadNotifications(id, clientId)
    }).future.flatMap {
      case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
        futureHistoryResults(response.notifications, response.beTime, firstSync = firstSync)
      case Right(LoadNotificationsResult(response, historyLost)) if response.hasMore && !historyLost =>
        load(response.notifications.lastOption.map(_.id)).flatMap {
          case Some(results) =>
            futureHistoryResults(
              response.notifications ++ results.notifications,
              if (results.time.isDefined) results.time else response.beTime,
              historyLost = results.historyLost
            )
          case None => Future.successful(None)
        }
      case Right(LoadNotificationsResult(response, historyLost)) if lastId.isDefined && historyLost =>
        warn(l"/notifications failed with 404, history lost")
        futureHistoryResults(response.notifications, response.beTime, historyLost)
      case Left(e@ErrorResponse(ResponseCode.Unauthorized, _, _)) =>
        warn(l"Logged out, failing sync request")
        Future.failed(FetchFailedException(e))
      case Left(err) =>
        warn(l"Request failed due to $err: attempting to load last page (since id: $lastId) again? $withRetries")
        if (!withRetries) CancellableFuture.failed(FetchFailedException(err))
        else {
          //We want to retry the download after the backoff is elapsed and the network is available,
          //OR on a network state change (that is not offline/unknown)
          //OR on a websocket state change
          val retry = Promise[Unit]()

          network.networkMode.onChanged.filter(!NetworkOff.contains(_)).next.map(_ => retry.trySuccess({}))
          wsPushService.connected.onChanged.next.map(_ => retry.trySuccess({}))

          for {
            _ <- CancellableFuture.delay(syncHistoryBackoff.delay(attempts))
            _ <- lift(network.networkMode.filter(!NetworkOff.contains(_)).head)
          } yield retry.trySuccess({})

          retry.future.flatMap { _ => load(lastId, firstSync, attempts + 1) }
        }
    }

  private def syncHistory(source: SyncSource, withRetries: Boolean = true): Future[Unit] = {
    verbose(l"Sync history in response to $source ($timePassed)")
    idPref().flatMap(lastId =>
      load(lastId, firstSync = lastId.isEmpty, withRetries = withRetries)
    ).flatMap {
      case Some(Results(nots, _, true, _)) =>
        idPref.update(nots.headOption.map(_.id)).map(_ => None)
      case Some(Results(_, time, _, true)) =>
        sync.performFullSync()
          .map(_ => onHistoryLost ! clock.instant())
          .map(_ => updateDrift(time))
          .map(_ => None)
      case Some(Results(nots, time, _, _)) if nots.nonEmpty =>
        updateDrift(time)
          .flatMap(_ => storeNotifications(nots))
          .flatMap(_ => syncHistory(source, withRetries = withRetries))
      case Some(Results(_, time, _, _)) =>
        updateDrift(time).map(_ => None)
      case None => Future.successful(None)
    }
  }
}

object PushService {
  val NetworkOff = Set(UNKNOWN, OFFLINE)

  //These are the most important event types that generate push notifications
  val TrackingEvents = Set("conversation.otr-message-add", "conversation.create", "conversation.rename", "conversation.member-join")

  val PipelineKey = "pipeline_processing"

  case class FetchFailedException(err: ErrorResponse) extends Exception(s"Failed to fetch notifications: ${err.message}")

  var syncHistoryBackoff: Backoff = new ExponentialBackoff(3.second, 15.seconds)

  sealed trait SyncSource
  case class FetchFromJob(nId: Option[Uid]) extends SyncSource
  case class FetchFromIdle(nId: Option[Uid]) extends SyncSource
  case class WebSocketChange(connected: Boolean) extends SyncSource
  object ForceSync extends SyncSource

  implicit val SyncSourceLogShow: LogShow[SyncSource] = LogShow.createFrom {
    case FetchFromJob(nId)          => l"FetchFromJob(nId: $nId)"
    case FetchFromIdle(nId)         => l"FetchFromIdle(nId: $nId)"
    case WebSocketChange(connected) => l"WebSocketChange(connected: $connected)"
    case ForceSync                  => l"ForcePush"
  }

  sealed trait SyncMode
  case class StoreNotifications(notifications: Seq[PushNotificationEncoded]) extends SyncMode

  //set withRetries to false if the caller is to handle their own retry logic
  case class SyncHistory(source: SyncSource, withRetries: Boolean = true) extends SyncMode

  case class Results(notifications: Vector[PushNotificationEncoded], time: Option[Instant], firstSync: Boolean, historyLost: Boolean)

}
