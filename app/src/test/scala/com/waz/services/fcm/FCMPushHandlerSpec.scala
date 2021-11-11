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
package com.waz.services.fcm

import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, Domain, Uid, UserId}
import com.waz.service.otr.OtrEventDecoder
import com.waz.service.push.PushNotificationEventsStorage
import com.waz.sync.client.PushNotificationsClient.{LoadNotificationsResponse, LoadNotificationsResult}
import com.waz.sync.client.{EncodedEvent, ErrorOrResponse, PushNotificationEncoded, PushNotificationsClient}
import com.wire.signals.{CancellableFuture, DispatchQueue, SerialDispatchQueue, Threading}
import org.junit.runner.RunWith
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers, OneInstancePerTest, Suite}
import org.scalatest.junit.JUnitRunner
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class FCMPushHandlerSpec extends FeatureSpec
  with Matchers
  with BeforeAndAfterAll
  with OneInstancePerTest
  with MockFactory { this: Suite =>
  import FCMPushHandlerSpec._
  implicit val dispatcher: DispatchQueue = SerialDispatchQueue()

  override def beforeAll(): Unit = {
    Threading.setAsDefault(dispatcher)
  }

  private val client      = mock[PushNotificationsClient]
  private val storage     = mock[PushNotificationEventsStorage]
  private val decoder     = mock[OtrEventDecoder]
  private val globalPrefs = new TestGlobalPreferences
  private val userPrefs   = new TestUserPreferences

  private def handler = FCMPushHandler(clientId, client, storage, decoder, globalPrefs, userPrefs)

  private def result[T](scenario: CancellableFuture[T]): T = result(scenario.future)
  private def result[T](scenario: Future[T]): T = Await.result(scenario, 5.seconds)

  private def success[T](result: T): ErrorOrResponse[T] = CancellableFuture.successful(Right(result))

  scenario("Don't load notifications when the last id is empty") {
    (client.loadNotifications _).expects(*, *).never()
    val scenario = for {
      _ <- CancellableFuture.lift(userPrefs.preference(LastStableNotification) := None)
      _ <- handler.syncNotifications()
    } yield ()
    result(scenario) shouldEqual (())
  }

  scenario("A new notification should update the last id") {
    val lastId = Uid()
    val newId = Uid()

    val res = wrapInResult(Vector(PushNotificationEncoded(newId, Vector(EncodedEvent(eventJsonStr())))))

    (client.loadNotifications _).expects(Some(lastId), clientId).once().returning(success(res))
    (client.loadNotifications _).expects(Some(newId), clientId).once().returning(success(emptyResult))
    (storage.saveAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq.empty))
    (decoder.decryptStoredOtrEvent _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Right(())))

    val lastIdPref = userPrefs.preference(LastStableNotification)

    val scenario = for {
      _  <- lastIdPref := Some(lastId)
      _  <- globalPrefs.preference(BackendDrift) := org.threeten.bp.Duration.ZERO
      _  <- handler.syncNotifications().future
      id <- lastIdPref()
    } yield id
    result(scenario) shouldEqual Some(newId)
  }

  scenario("A new notification should be saved to the storage") {
    val lastId = Uid()
    val newId = Uid()

    val notifications = Vector(PushNotificationEncoded(newId, Vector(EncodedEvent(eventJsonStr()))))

    val res = wrapInResult(notifications)
    (client.loadNotifications _).expects(Some(lastId), clientId).once().returning(success(res))
    (client.loadNotifications _).expects(Some(newId), clientId).once().returning(success(emptyResult))
    (storage.saveAll _).expects(notifications).once().returning(Future.successful(Seq.empty))
    (decoder.decryptStoredOtrEvent _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Right(())))

    val lastIdPref = userPrefs.preference(LastStableNotification)

    val scenario = for {
      _  <- lastIdPref := Some(lastId)
      _  <- globalPrefs.preference(BackendDrift) := org.threeten.bp.Duration.ZERO
      _  <- handler.syncNotifications().future
    } yield ()
    result(scenario)
  }

  scenario("If a new notification comes while the first is processed, we should load it too") {
    val lastId = Uid()
    val newId1 = Uid()
    val newId2 = Uid()

    val nots1 = Vector(PushNotificationEncoded(newId1, Vector(EncodedEvent(eventJsonStr()))))
    val nots2 = Vector(PushNotificationEncoded(newId2, Vector(EncodedEvent(eventJsonStr()))))

    val res1 = wrapInResult(nots1)
    val res2 = wrapInResult(nots2)
    (client.loadNotifications _).expects(Some(lastId), clientId).once().returning(success(res1))
    (client.loadNotifications _).expects(Some(newId1), clientId).once().returning(success(res2))
    (client.loadNotifications _).expects(Some(newId2), clientId).once().returning(success(emptyResult))
    (storage.saveAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq.empty))
    (decoder.decryptStoredOtrEvent _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Right(())))

    val lastIdPref = userPrefs.preference(LastStableNotification)

    val scenario = for {
      _  <- lastIdPref := Some(lastId)
      _  <- globalPrefs.preference(BackendDrift) := org.threeten.bp.Duration.ZERO
      _  <- handler.syncNotifications().future
    } yield ()
    result(scenario)
  }
}

object FCMPushHandlerSpec {
  val convId: ConvId = ConvId()
  val userId: UserId = UserId()
  val clientId: ClientId = ClientId()
  val domain: Domain = Domain("staging.zinfra.io")

  def eventJsonStr(convId: ConvId = this.convId,
                   userId: UserId = this.userId,
                   clientId: ClientId = this.clientId,
                   domain: Domain = this.domain): String =
    s"""
       |      {
       |        "qualified_conversation": {
       |          "domain": "${domain.str}",
       |          "id": "${convId.str}"
       |        },
       |        "conversation": "${convId.str}",
       |        "time": "2021-11-08T16:31:28.872Z",
       |        "data": {
       |          "text": "encoded_string",
       |          "data": "",
       |          "sender": "a693b2dea78f634c",
       |          "recipient": "${clientId.str}"
       |        },
       |        "from": "${userId.str}",
       |        "qualified_from": {
       |          "domain": "${domain.str}",
       |          "id": "$userId"
       |        },
       |        "type": "conversation.otr-message-add"
       |      }
       |""".stripMargin.trim

  def wrapInResult(notifications: Vector[PushNotificationEncoded],
                   hasMore: Boolean = false,
                   beTime: Option[Instant] = None,
                   historyLost: Boolean = false): LoadNotificationsResult =
    LoadNotificationsResult(
      LoadNotificationsResponse(
        notifications = notifications,
        hasMore = hasMore,
        beTime = beTime
      ),
      historyLost = historyLost
    )

  val emptyResult: LoadNotificationsResult =
    LoadNotificationsResult(
      LoadNotificationsResponse(Vector.empty, hasMore = false, beTime = None),
      historyLost = false
    )
}
