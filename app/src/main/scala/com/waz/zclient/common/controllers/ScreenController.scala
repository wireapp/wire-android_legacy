/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.common.controllers

import android.content.Context
import com.waz.model.MessageId
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingDestination
import com.waz.zclient.conversation.LikesAndReadsFragment
import com.waz.zclient.drawing.DrawingFragment
import com.waz.zclient.{Injectable, Injector}

class ScreenController(implicit injector: Injector, context: Context) extends Injectable {

  def openOtrDevicePreferences(): Unit = context.startActivity(ShowDevicesIntent)

  val showMessageDetails = Signal(Option.empty[MessageDetailsParams])

  val showGiphy = EventStream[Option[String]]()

  val hideGiphy = EventStream[Boolean] //true if successfully sent gif

  val showSketch = EventStream[DrawingFragment.Sketch]

  val hideSketch = EventStream[DrawingDestination]

  def hideSketchJava(dest: DrawingDestination) = hideSketch ! dest


}

object ScreenController {
  case class MessageDetailsParams(messageId: MessageId, tab: LikesAndReadsFragment.Tab)
}
