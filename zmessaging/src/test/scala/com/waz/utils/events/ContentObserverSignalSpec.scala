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
package com.waz.utils.events

import android.content.Context
import com.waz.RobolectricUtils
import com.waz.service.ContactsServiceImpl
import com.waz.utils.returning
import org.scalatest._
import org.threeten.bp.Instant

@Ignore class ContentObserverSignalSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfterAll with RobolectricTests with RobolectricUtils {
  import EventContext.Implicits.global

  scenario("Subscribe on signal") {
    check((signal, received) => returning(signal(v => received.value = Some(v)))(_ => received.value shouldEqual Some(None)))
  }

  scenario("Subscribe on signal change") {
    check((signal, received) => returning(signal.onChanged(v => received.value = Some(v)))(_ => received.value shouldBe None))
  }

  def check(createSubscription: (Signal[Option[Instant]], Var) => Subscription) = {
    val received = Var(None)
    var signal: Signal[Option[Instant]] = new ContentObserverSignal(ContactsServiceImpl.Contacts)
    var sub = createSubscription(signal, received)
    val before = received.value.flatten
    Thread.sleep(1)
    notifyChanged()
    received.value.flatten.exists(a => before.forall(_.isBefore(a))) shouldBe true

    signal = null
    received.value = None
    System.gc()
    notifyChanged()
    received.value.exists(_.isDefined) shouldBe true

    sub = null
    received.value = None
    System.gc()
    notifyChanged()
    received.value shouldBe None
  }

  case class Var(var value: Option[Option[Instant]])

  def notifyChanged() = implicitly[Context].getContentResolver.notifyChange(ContactsServiceImpl.Contacts, null)
}
