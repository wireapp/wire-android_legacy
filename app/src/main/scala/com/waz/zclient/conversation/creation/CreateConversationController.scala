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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.ShouldCreateFullConversation
import com.waz.model.{ConvId, UserId}
import com.waz.service.ZMessaging
import com.waz.service.tracking._
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.utils.UiStorage
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class CreateConversationController(implicit inj: Injector, ev: EventContext) extends Injectable {
  import com.waz.threading.Threading.Implicits.Background

  lazy val onShowCreateConversation = EventStream[Boolean]()

  private lazy val conversationController = inject[ConversationController]
  private lazy val zms = inject[Signal[ZMessaging]]

  private implicit lazy val uiStorage = inject[UiStorage]
  private lazy val tracking = inject[TrackingService]

  val convId   = Signal(Option.empty[ConvId])
  val name     = Signal("")
  val users    = Signal(Set.empty[UserId])
  val teamOnly = Signal(true)
  val fromScreen = Signal[GroupConversationEvent.Method]()

  teamOnly.onChanged {
    case true =>
      for {
        z   <- zms.head
        ids <- users.head
        us  <- z.usersStorage.listAll(ids).map(_.toSet)
      } yield users.mutate(_ -- us.filter(_.isGuest(z.teamId)).map(_.id))
    case false => //
  }

  def setCreateConversation(preSelectedUsers: Set[UserId] = Set(), from: GroupConversationEvent.Method): Unit = {
    name ! ""
    users ! preSelectedUsers
    convId ! None
    fromScreen ! from
    teamOnly ! false
    tracking.track(CreateGroupConversation(from))
  }

  def setAddToConversation(conv: ConvId): Unit = {
    name ! ""
    users ! Set()
    convId ! Some(conv)
    fromScreen ! GroupConversationEvent.ConversationDetails
  }

  def createConversation(): Future[ConvId] =
    for {
      z                   <- zms.head
      name                <- name.head
      userIds             <- users.head
      shouldFullConv      <- inject[GlobalPreferences].preference(ShouldCreateFullConversation).apply()
      _ = verbose(s"creating conv with ${userIds.size} users and shouldFullConv == $shouldFullConv")
      userIds             <-
        if (userIds.isEmpty && shouldFullConv) {
          z.usersStorage.list().map(
            _.filter(u => (u.isConnected || (u.teamId.isDefined && u.teamId == z.teamId)) && u.id != z.selfUserId).map(_.id).toSet.take(255)
          )
        } else Future.successful(userIds)
      _ = verbose(s"creating conv with ${userIds.size} users")
      teamOnly            <- teamOnly.head
      conv                <- conversationController.createGroupConversation(Some(name.trim), userIds, teamOnly)
      from                <- fromScreen.head
      (guests, nonGuests) <- z.usersStorage.getAll(userIds).map(_.flatten.partition(_.isGuest(z.teamId)))
    } yield {
      tracking.track(AddParticipantsEvent(!teamOnly, nonGuests.size + 1, guests.size, from))
      tracking.track(GroupConversationSuccessful(userIds.nonEmpty, !teamOnly, from))
      conv.id
    }

  def addUsersToConversation(): Future[Unit] = {
    for {
      z            <- zms.head
      Some(convId) <- convId.head
      Some(conv)   <- z.convsStorage.get(convId)
      userIds      <- users.head
      from         <- fromScreen.head
      _            <- conversationController.addMembers(convId, userIds)
      (guests, nonGuests) <- z.usersStorage.getAll(userIds).map(_.flatten.partition(_.isGuest(z.teamId)))
    } yield {
      tracking.track(AddParticipantsEvent(!conv.isTeamOnly, nonGuests.size, guests.size, from))
    }
  }
}
