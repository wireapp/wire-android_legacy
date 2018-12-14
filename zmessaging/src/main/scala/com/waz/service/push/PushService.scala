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
import com.waz.ZLog.LogTag
import com.waz.log.ZLog2._
import com.waz.api.NetworkMode.{OFFLINE, UNKNOWN}
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.model.Event.EventDecoder
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.{accountTag, clock}
import com.waz.service._
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService.SyncSource
import com.waz.service.tracking.{MissedPushEvent, ReceivedPushEvent, TrackingService}
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

  //set withRetries to false if the caller is to handle their own retry logic
  def syncHistory(reason: SyncSource, withRetries: Boolean = true): Future[Unit]

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
                      receivedPushes:       ReceivedPushStorage,
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
                      timeouts:             Timeouts)
                     (implicit ev: AccountContext) extends PushService { self =>
  import PushService._

  implicit val logTag: LogTag = accountTag[PushServiceImpl](selfUserId)
  private implicit val dispatcher = new SerialDispatchQueue(name = "PushService")

  override val onHistoryLost = new SourceSignal[Instant] with BgEventSource
  override val processing = Signal(false)

  override def waitProcessing =
    processing.filter(_ == false).head.map(_ => {})

  private val beDriftPref = prefs.preference(BackendDrift)
  override val beDrift = beDriftPref.signal.disableAutowiring()

  private var fetchInProgress = Future.successful({})

  private lazy val idPref = userPrefs.preference(LastStableNotification)

  private var subs = Seq.empty[Subscription]

  notificationStorage.registerEventHandler { () =>
    returning {
      Serialized.future(PipelineKey) {
        processing ! true
        for {
          _ <- processEncryptedRows()
          _ <- processDecryptedRows()
        } yield processing ! false
      }
    }(_.failed.foreach { e =>
      processing ! false
      throw e
    })
  }

  private def processEncryptedRows() =
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
      })
    }

  private def processDecryptedRows(): Future[Unit] = {
    def decodeRow(event: PushNotificationEvent) =
      if(event.plain.isDefined && isOtrEventJson(event.event)) {
        val msg = GenericMessage(event.plain.get)
        val msgEvent = ConversationEvent.ConversationEventDecoder(event.event)
        otrService.parseGenericMessage(msgEvent.asInstanceOf[OtrMessageEvent], msg)
      } else {
        Some(EventDecoder(event.event))
      }

    notificationStorage.getDecryptedRows().flatMap { rows =>
      verbose(l"Processing ${rows.size} rows")
      if (rows.nonEmpty)
        for {
          _ <- pipeline(rows.flatMap(decodeRow))
          _ <- notificationStorage.removeRows(rows.map(_.index))
          _ <- processDecryptedRows()
        } yield {}
      else Future.successful(())
    }
  }

  wsPushService.notifications() { notifications =>
    fetchInProgress =
      if (fetchInProgress.isCompleted) storeNotifications(notifications)
      else fetchInProgress.flatMap(_ => storeNotifications(notifications))
  }

  wsPushService.connected().onChanged.map(WebSocketChange).on(dispatcher)(syncHistory(_))

  private def storeNotifications(notifications: Seq[PushNotificationEncoded]): Future[Unit] =
    Serialized.future(PipelineKey)(notificationStorage.saveAll(notifications).flatMap { _ =>
      notifications.lift(notifications.lastIndexWhere(!_.transient)).fold(Future.successful(()))(n => idPref := Some(n.id))
    })

  private def isOtrEventJson(ev: JSONObject) =
    ev.getString("type").equals("conversation.otr-message-add")

  case class Results(notifications: Vector[PushNotificationEncoded], time: Option[Instant], firstSync: Boolean, historyLost: Boolean)

  private def futureHistoryResults(notifications: Vector[PushNotificationEncoded] = Vector.empty,
                                   time: Option[Instant] = None,
                                   firstSync: Boolean = false,
                                   historyLost: Boolean = false) =
    CancellableFuture.successful(Results(notifications, time, firstSync, historyLost))

  //expose retry loop to tests
  protected[push] val waitingForRetry: SourceSignal[Boolean] = Signal(false).disableAutowiring()

  override def syncHistory(source: SyncSource, withRetries: Boolean = true): Future[Unit] = {

    def load(lastId: Option[Uid], firstSync: Boolean = false, attempts: Int = 0): CancellableFuture[Results] =
      (lastId match {
        case None => if (firstSync) client.loadLastNotification(clientId) else client.loadNotifications(None, clientId)
        case id   => client.loadNotifications(id, clientId)
      }).flatMap {
        case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
          futureHistoryResults(response.notifications, response.beTime, firstSync = firstSync)
        case Right(LoadNotificationsResult(response, historyLost)) if response.hasMore && !historyLost =>
          load(response.notifications.lastOption.map(_.id)).flatMap { results =>
            futureHistoryResults(
              response.notifications ++ results.notifications,
              if (results.time.isDefined) results.time else response.beTime,
              historyLost = results.historyLost
            )
          }
        case Right(LoadNotificationsResult(response, historyLost)) if lastId.isDefined && historyLost =>
          warn(l"/notifications failed with 404, history lost")
          futureHistoryResults(response.notifications, response.beTime, historyLost)
        case Left(e @ ErrorResponse(ResponseCode.Unauthorized, _, _)) =>
          warn(l"Logged out, failing sync request")
          CancellableFuture.failed(FetchFailedException(e))
        case Left(err) =>
          warn(l"Request failed due to $err: attempting to load last page (since id: $lastId) again? $withRetries")
          if (!withRetries) CancellableFuture.failed(FetchFailedException(err))
          else {
            //We want to retry the download after the backoff is elapsed and the network is available,
            //OR on a network state change (that is not offline/unknown)
            //OR on a websocket state change
            val retry = Promise[Unit]()

            network.networkMode.onChanged.filter(!Set(UNKNOWN, OFFLINE).contains(_)).next.map(_ => retry.trySuccess({}))
            wsPushService.connected().onChanged.next.map(_ => retry.trySuccess({}))

            for {
              _ <- CancellableFuture.delay(syncHistoryBackoff.delay(attempts))
              _ <- lift(network.networkMode.filter(!Set(UNKNOWN, OFFLINE).contains(_)).head)
            } yield retry.trySuccess({})

            waitingForRetry ! true
            lift(retry.future).flatMap { _ =>
              waitingForRetry ! false
              load(lastId, firstSync, attempts + 1)
            }
          }
      }

    def syncHistory(lastId: Option[Uid]): Future[Unit] =
      load(lastId, firstSync = lastId.isEmpty).future.flatMap {
        case Results(nots, time, firstSync, historyLost) =>
          if (firstSync) idPref := nots.headOption.map(_.id)
          else
            (for {
              _ <- if (historyLost) sync.performFullSync().map(_ => onHistoryLost ! clock.instant()) else Future.successful({})
              _ <- beDriftPref.mutate(v => time.map(clock.instant.until(_)).getOrElse(v))
            } yield {
              reportMissing(nots)
              nots
            }).flatMap(storeNotifications)
      }

    def reportMissing(nots: Vector[PushNotificationEncoded]): Unit =
      for {
        now    <- beDrift.head.map(clock.instant + _) //get time at fetch (before waiting)
        _      <- CancellableFuture.delay(5.seconds).future //wait a few seconds for any lagging FCM notifications before doing comparison
        nw     <- network.networkMode.head
        pushes <- receivedPushes.list()
        _      <- receivedPushes.removeAll(pushes.map(_.id))
        inBackground <- lifeCycle.uiActive.map(!_).head
      } {
        val sourcePush = source match {
          case FetchFromJob(nId) => nId
          case FetchFromIdle(nId) => nId
          case _ => None
        }

        val notsUntilPush = nots.takeWhile(n => !sourcePush.contains(n.id))

        val missedEvents = notsUntilPush.filterNot(_.transient).map { n =>

          val events =
            JsonDecoder.array(n.events, { case (arr, i) => arr.getJSONObject(i) })
              .filter(ev => TrackingEvents(ev.getString("type")))
              .filter(ev => UserId(ev.getString("from")) != selfUserId)
              .map(_.getString("type"))

          (n.id, events)
        }.filter { case (id, evs) => evs.nonEmpty && !pushes.map(_.id).contains(id) }

        val allEvents = missedEvents.toMap.values.flatten

        val eventFrequency = TrackingEvents.map(e => (e, allEvents.count(_ == e))).toMap

        if (missedEvents.nonEmpty) //we didn't get pushes for some returned notifications
          tracking.track(MissedPushEvent(now, missedEvents.size, inBackground, nw, network.getNetworkOperatorName, eventFrequency, missedEvents.last._1.str))

        if (pushes.nonEmpty)
          pushes.map(p => p.copy(toFetch = Some(p.receivedAt.until(now)))).foreach(p => tracking.track(ReceivedPushEvent(p)))
      }

    if (fetchInProgress.isCompleted) {
      verbose(l"Sync history in response to $source")
      fetchInProgress = idPref().flatMap(syncHistory)
    }
    fetchInProgress
  }
}

object PushService {

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

  implicit val SyncSourceLogShow: LogShow[SyncSource] =
    LogShow.createFrom {
      case FetchFromJob(nId) => l"FetchFromJob(nId: $nId)"
      case FetchFromIdle(nId) => l"FetchFromIdle(nId: $nId)"
      case WebSocketChange(connected) => l"WebSocketChange(connected: $connected)"
      case ForceSync => l"ForcePush"
    }
}
