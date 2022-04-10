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
package com.waz.service
import com.waz.content.{PropertiesStorage, PropertyValue, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ReadReceiptEnabledPropertyEvent, SyncId}
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.TestUserPreferences
import com.waz.threading.Threading
import com.wire.signals.Signal

import scala.concurrent.Future

class PropertiesServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val sync    = mock[SyncServiceHandle]
  private lazy val storage = mock[PropertiesStorage]
  private lazy val prefs   = new TestUserPreferences()
  private lazy val req     = mock[SyncRequestService]
  private lazy val push    = mock[PushService]

  private lazy val service   = new PropertiesServiceImpl(prefs, sync, storage, req, push)
  private lazy val scheduler = new EventScheduler(Stage(Sequential)(service.eventProcessor))
  private lazy val pipeline  = new EventPipeline(scheduler)

  feature("Properties"){

    scenario("Read receipts property event should update storage and flag preferences") {
      val event = ReadReceiptEnabledPropertyEvent(1)

      (storage.find _).expects(PropertyKey.ReadReceiptsEnabled).anyNumberOfTimes().returning(Future.successful(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "0"))))

      (storage.save _).expects(PropertyValue(PropertyKey.ReadReceiptsEnabled, "1")).once().returning(Future.successful({}))

      val res = pipeline.apply(Seq(event)).flatMap { _ =>
        prefs(UserPreferences.ReadReceiptsRemotelyChanged).apply()
      }

      result(res) shouldBe true
    }

    scenario("Read receipts util signal should correctly convert 1 to true") {
      (storage.optSignal _).expects(PropertyKey.ReadReceiptsEnabled).atLeastOnce().returning(Signal.const(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "1"))))
      (storage.find _).expects(PropertyKey.ReadReceiptsEnabled).atLeastOnce().returning(Future.successful(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "1"))))
      result(service.readReceiptsEnabled.head) shouldBe true
    }

    scenario("Read receipts util signal should correctly convert 0 to false") {
      (storage.optSignal _).expects(PropertyKey.ReadReceiptsEnabled).atLeastOnce().returning(Signal.const(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "0"))))
      (storage.find _).expects(PropertyKey.ReadReceiptsEnabled).atLeastOnce().returning(Future.successful(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "0"))))
      result(service.readReceiptsEnabled.head) shouldBe false
    }

    scenario("Set read receipts option should update the property and sync the value") {
      (storage.find _).expects(PropertyKey.ReadReceiptsEnabled).atLeastOnce().returning(Future.successful(Some(PropertyValue(PropertyKey.ReadReceiptsEnabled, "0"))))
      (storage.save _).expects(PropertyValue(PropertyKey.ReadReceiptsEnabled, "1")).once().returning(Future.successful({}))
      (sync.postProperty (_: PropertyKey, _: Int)).expects(PropertyKey.ReadReceiptsEnabled, 1).once().returning(Future.successful(SyncId()))
      result(service.setReadReceiptsEnabled(true))
    }
  }
}
