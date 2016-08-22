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
package com.waz.zclient.controllers.context

import java.util.concurrent.TimeUnit

import android.content._
import com.waz.model.ConvId
import com.waz.testutils.TestUtils.{PrintValues, signalTest}
import com.waz.testutils.TestWireContext
import com.waz.utils.events.{SourceSignal, EventContext, Signal}
import com.waz.zclient.Module
import com.waz.zclient.controllers.global.KeyboardController
import org.junit.{Before, Test}
import org.mockito.Mockito._
import org.scalatest.RobolectricSuite
import org.scalatest.junit.JUnitSuite

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class ScrollControllerTest extends JUnitSuite with RobolectricSuite {

  implicit val defaultDuration = Duration(30, TimeUnit.SECONDS)

  implicit val printSignalVals: PrintValues = true

  implicit val context = mock(classOf[TestWireContext])
  implicit val eventContext = EventContext.Global
  implicit val executionContext = ExecutionContext.Implicits.global

  lazy val keyboardController = mock(classOf[KeyboardController])
  lazy val keyboardVisibility = Signal[Boolean]

  lazy val mockAdapter = mock(classOf[TestAdapter])

  var lastReadSig: SourceSignal[Int] = _
  var countSig: SourceSignal[Int] = _
  var convSig: SourceSignal[ConvId] = _

  implicit lazy val module = new Module {
    bind[Context] to context
    bind[KeyboardController] to keyboardController
  }

  @Before
  def setup(): Unit = {

    lastReadSig = Signal()
    countSig = Signal()
    convSig = Signal()

    when(mockAdapter.msgCount).thenReturn(countSig)
    when(mockAdapter.selectedConversation).thenReturn(convSig)
    when(mockAdapter.lastReadIndex).thenReturn(lastReadSig)

    when(context.eventContext).thenReturn(EventContext.Global)
    when(keyboardController.keyboardVisibility).thenReturn(keyboardVisibility)
  }

  @Test
  def scrolledToBottom(): Unit = {
    val sctrl = new ScrollController()
    val mockAdapter = mock(classOf[TestAdapter])

    val countSig = Signal[Int]

    when(mockAdapter.msgCount).thenReturn(countSig)
    sctrl.adapter ! mockAdapter

    signalTest(sctrl.scrolledToBottom)(_ == true) {
      countSig ! 11
      sctrl.lastVisibleItemIndex ! 10
    }

    signalTest(sctrl.scrolledToBottom)(_ == false) {
      countSig ! 12
    }
  }

  @Test
  def openDifferentConversationShouldScrollToLastRead(): Unit = {
    val sctrl = new ScrollController()
    sctrl.adapter ! mockAdapter

    //Test first conversation
    signalTest(sctrl.scrollPosition)(_ == 50) {
      convSig ! ConvId()
      lastReadSig ! 50
      sctrl.lastVisibleItemIndex ! -1 //should not yet be known.
      countSig ! 100
    }

    //Change conversation again
    signalTest(sctrl.scrollPosition)(_ == 100) {
      convSig ! ConvId()
      lastReadSig ! 100
      countSig ! 150
    }

  }

  @Test
  def receiveMessageWhenScrolledToBottom(): Unit = {

  }


  abstract class TestAdapter extends Adapter

}
