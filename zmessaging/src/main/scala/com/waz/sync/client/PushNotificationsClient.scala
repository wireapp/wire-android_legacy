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
package com.waz.sync.client

import com.waz.log.LogSE._
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.sync.client.PushNotificationsClient.LoadNotificationsResult
import com.waz.utils.JsonDecoder.arrayColl
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request, ResponseCode, _}
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.util.control.NonFatal

//TODO Think about returning models.
trait PushNotificationsClient {
  def loadNotifications(since: Option[Uid], client: ClientId): ErrorOrResponse[LoadNotificationsResult]
  def loadLastNotification(clientId: ClientId): ErrorOrResponse[LoadNotificationsResult]
}

class PushNotificationsClientImpl(pageSize: Int = PushNotificationsClient.PageSize)
                                 (implicit
                                  urlCreator: UrlCreator,
                                  httpClient: HttpClient,
                                  authRequestInterceptor: AuthRequestInterceptor) extends PushNotificationsClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import PushNotificationsClient._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val loadNotifResponseDeserializer: RawBodyDeserializer[LoadNotificationsResponse] =
    RawBodyDeserializer[JSONObject].map(json => PagedNotificationsResponse.unapply(JsonObjectResponse(json)).get)

  override def loadNotifications(since: Option[Uid], client: ClientId): ErrorOrResponse[LoadNotificationsResult] = {
    Request
      .Get(
        relativePath = NotificationsPath,
        queryParameters = queryParameters("since" -> since, "client" -> client, "size" -> pageSize)
      )
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[Response[LoadNotificationsResponse]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        LoadNotificationsResult(response.body, historyLost = response.code == ResponseCode.NotFound)
      }
  }

  override def loadLastNotification(clientId: ClientId): ErrorOrResponse[LoadNotificationsResult] = {
    Request
      .Get(relativePath = NotificationsLastPath, queryParameters = queryParameters("client" -> clientId))
      .withResultType[PushNotificationEncoded]
      .withErrorType[ErrorResponse]
      .executeSafe { notif =>
        LoadNotificationsResult(
          LoadNotificationsResponse(Vector(notif), hasMore = false, beTime = None),
          historyLost = false
        )
      }
  }
}

object PushNotificationsClient {

  val NotificationsPath = "/notifications"
  val NotificationsLastPath = "/notifications/last"
  val PageSize = 500

  final case class LoadNotificationsResult(response: LoadNotificationsResponse, historyLost: Boolean)

  final case class LoadNotificationsResponse(notifications: Vector[PushNotificationEncoded],
                                             hasMore: Boolean,
                                             beTime: Option[Instant])

  object PagedNotificationsResponse extends DerivedLogTag {

    import com.waz.utils.JsonDecoder._

    def unapply(response: ResponseContent): Option[LoadNotificationsResponse] = try response match {
      case JsonObjectResponse(js) if js.has("notifications") =>
        Some(
          LoadNotificationsResponse(
            arrayColl[PushNotificationEncoded, Vector](js.getJSONArray("notifications")),
            decodeBool('has_more)(js),
            decodeOptISOInstant('time)(js)
          )
        )
      case JsonArrayResponse(js) =>
        Some(
          LoadNotificationsResponse(
            arrayColl[PushNotificationEncoded, Vector](js),
            hasMore = false,
            None)
        )
      case _ => None
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse paged push notifications from response:", e)
        None
    }
  }

  object NotificationsResponseEncoded extends DerivedLogTag {
    def unapplySeq(response: ResponseContent): Option[Seq[PushNotificationEncoded]] = try response match {
      case JsonObjectResponse(js) => Some(Vector(implicitly[JsonDecoder[PushNotificationEncoded]].apply(js)))
      case JsonArrayResponse(js) => Some(arrayColl[PushNotificationEncoded, Vector](js))
      case _ => None
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse push notification(s) from response:", e)
        None
    }
  }
}

final case class PushNotificationEncoded(id: Uid, events: JSONArray, transient: Boolean = false)

final case class PushNotification(id: Uid, events: Seq[Event], transient: Boolean = false) {

  /**
    * Check if notification contains events intended for current client. In some (rare) cases it may happen that
    * BE sends us notifications intended for different device, we can verify that by checking recipient field.
    * Unencrypted events are always considered to belong to us.
    */
  def hasEventForClient(clientId: ClientId) = events.forall(forUs(clientId, _))

  def eventsForClient(clientId: ClientId) = events.filter(forUs(clientId, _))

  private def forUs(clientId: ClientId, event: Event) = event match {
    case ev: OtrEvent => clientId == ev.recipient
    case _ => true
  }
}

object PushNotification {
  implicit lazy val NotificationDecoder: JsonDecoder[PushNotification] = new JsonDecoder[PushNotification] {
    import com.waz.utils.JsonDecoder._

    override def apply(implicit js: JSONObject): PushNotification =
      PushNotification('id, array[Event](js.getJSONArray("payload")), 'transient)
  }
}

object PushNotificationEncoded {
  implicit lazy val NotificationDecoder: JsonDecoder[PushNotificationEncoded] =
    new JsonDecoder[PushNotificationEncoded] {
      import com.waz.utils.JsonDecoder._

    override def apply(implicit js: JSONObject): PushNotificationEncoded =
      PushNotificationEncoded('id, js.getJSONArray("payload"), 'transient)
  }
  implicit lazy val NotificationEncoder: JsonEncoder[PushNotificationEncoded] = new JsonEncoder[PushNotificationEncoded] {
    override def apply(v: PushNotificationEncoded): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("payload", v.events)
      o.put("transient", v.transient)
    }
  }
}
