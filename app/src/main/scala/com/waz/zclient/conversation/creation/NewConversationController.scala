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
package com.waz.zclient.conversation.creation

import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{ConvId, UserId}
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class NewConversationController(implicit inj: Injector) extends Injectable {
  import com.waz.threading.Threading.Implicits.Background

  ZLog.verbose("NewConversationController started")

  private lazy val conversationController = inject[ConversationController]

  val convId: SourceSignal[ConvId] = Signal(ConvId())
  val name: SourceSignal[String] = Signal("")
  val users: SourceSignal[Set[UserId]] = Signal(Set.empty[UserId])

  def reset(): Unit = {
    name ! ""
    users ! Set()
    convId ! ConvId()
  }

  def createConversation(): Future[ConvId] =
    for {
      convId <- convId.head
      name <- name.head
      users <- users.head
      conv <- conversationController.createGroupConversation(users.toSeq, Some(name), convId)
    } yield conv.id

}
