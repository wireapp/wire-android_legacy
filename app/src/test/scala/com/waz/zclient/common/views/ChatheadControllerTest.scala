/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.common.views

import com.waz.api.impl.ContactDetails
import com.waz.model.NameSource.Nickname
import com.waz.model.UserData.ConnectionStatus.Accepted
import com.waz.model._
import com.waz.service.{SearchKey, ZMessaging}
import com.waz.testutils.TestUtils.{PrintValues, signalTest}
import com.waz.testutils.{MockUiModule, MockZMessaging}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.Module
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.scalatest.junit.JUnitSuite

import scala.collection.GenSet

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest=Config.NONE)
class ChatheadControllerTest extends JUnitSuite {

  implicit val printSignalVals: PrintValues = false
  implicit val eventContext = EventContext.Implicits.global

  var zMessaging: MockZMessaging = _
  var uiModule: MockUiModule = _

  implicit lazy val module = new Module {
    bind[Signal[ZMessaging]] to Signal.const(zMessaging)
  }

  @Before
  def setup(): Unit = {
    zMessaging = new MockZMessaging()
    uiModule = new MockUiModule(zMessaging)
  }

  @Test
  def borderWidth(): Unit = {
    borderWidthTest(setShowBorder = true, userKnown = true, shouldHaveWidth = true)
    borderWidthTest(setShowBorder = false, userKnown = true, shouldHaveWidth = false)
    borderWidthTest(setShowBorder = true, userKnown = false, shouldHaveWidth = false)
  }

  def borderWidthTest(setShowBorder: Boolean, userKnown: Boolean, shouldHaveWidth: Boolean) = {
    val userId = createUser(userKnown)
    val ctrl = new ChatheadController(showBorder = setShowBorder, border = Some(Border(0, 0, largeBorderWidth = 10)))

    signalTest(ctrl.borderWidth) { width =>
      if (shouldHaveWidth) {
        width > 0
      } else {
        width == 0
      }
    } {
      ctrl.assignInfo ! Left(userId)
      ctrl.viewWidth ! 100
    }
  }

  @Test
  def selectedState(): Unit = {
    setSelectedTest(knownUser = true, expectSelected = true)
    setSelectedTest(knownUser = false, expectSelected = false)
    setSelectedTest(useContact = true, expectSelected = false)
  }

  def setSelectedTest(useContact: Boolean = false, knownUser: Boolean = false, expectSelected: Boolean) = {
    val ctrl = new ChatheadController(setSelectable = true)
    signalTest(ctrl.selected) { selected =>
      selected == expectSelected
    } {
      if (useContact) {
        ctrl.assignInfo ! Right(createContactDetails())
      } else {
        ctrl.assignInfo ! Left(createUser(knownUser))
      }
      ctrl.requestSelected ! true
    }
  }

  def createContactDetails() = {
    new ContactDetails(new Contact(ContactId(), "Test Contact", Nickname, "", SearchKey(""), GenSet(), GenSet()), false)(uiModule)
  }

  def createUser(known: Boolean): UserId = {
    val userId = UserId()
    val connection = if (known) Accepted else UserData.ConnectionStatus.Unconnected
    zMessaging.insertUser(new UserData(userId, name = "Some Name", None, None, connection = connection, searchKey = SearchKey("")))
    userId
  }
}
