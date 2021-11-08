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

import com.waz.content.UserPreferences.LastStableNotification
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, UserId}
import com.waz.sync.client.{ErrorOrResponse, PushNotificationsClient}
import com.wire.signals.CancellableFuture
import org.scalamock.scalatest.MockFactory
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class FCMPushHandlerSpec extends FeatureSpec
  with Matchers
  with OneInstancePerTest
  with MockFactory {
  this: Suite =>

  private val userPrefs = new TestUserPreferences
  private val globalPrefs = new TestGlobalPreferences
  private val client = mock[PushNotificationsClient]
  private val clientId = ClientId()

  private def handler: FCMPushHandler = new FCMPushHandlerImpl(userPrefs, globalPrefs, client, clientId)

  private def result[T](scenario: CancellableFuture[T]): T = Await.result(scenario.future, 5.seconds)

  private def success[T](result: T): ErrorOrResponse[T] = CancellableFuture.successful(Right(result))

  scenario("Don't load notifications when the last id is empty") {
    (client.loadNotifications _).expects(*, *).never()
    val scenario = for {
      _ <- CancellableFuture.lift(userPrefs.preference(LastStableNotification) := None)
      _ <- handler.syncNotifications()
    } yield ()
    result(scenario) shouldEqual (())
  }
}
