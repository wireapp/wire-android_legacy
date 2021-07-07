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

import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.ShouldCreateFullConversation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.tracking.GroupConversationEvent
import com.waz.service.{IntegrationsService, ZMessaging}
import com.wire.signals.{EventStream, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.UiStorage
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class CreateConversationController(implicit inj: Injector)
  extends Injectable with DerivedLogTag  {

  import com.waz.threading.Threading.Implicits.Background

  lazy val onShowCreateConversation = EventStream[Boolean]()

  private lazy val conversationController = inject[ConversationController]
  private lazy val integrationsService    = inject[Signal[IntegrationsService]]
  private lazy val zms = inject[Signal[ZMessaging]]

  private implicit lazy val uiStorage = inject[UiStorage]

  val convId       = Signal(Option.empty[ConvId])
  val name         = Signal("")
  val users        = Signal(Set.empty[UserId])
  val integrations = Signal(Set.empty[(ProviderId, IntegrationId)])
  val teamOnly     = Signal(true)
  val readReceipts = Signal(true)
  val fromScreen   = Signal[GroupConversationEvent.Method]()

  teamOnly.onChanged.foreach {
    case true =>
      for {
        z   <- zms.head
        ids <- users.head
        us  <- z.usersStorage.listAll(ids).map(_.toSet)
      } yield users.mutate(_ -- us.filter(u => u.isGuest(z.teamId) || u.deleted).map(_.id))
    case false => //
  }

  def setCreateConversation(preSelectedUsers: Set[UserId] = Set(), from: GroupConversationEvent.Method): Unit = {
    name ! ""
    users ! preSelectedUsers
    integrations ! Set.empty
    convId ! None
    fromScreen ! from
    teamOnly ! false
  }

  def setAddToConversation(convId: ConvId): Unit = {
    name ! ""
    users ! Set.empty
    integrations ! Set.empty
    this.convId ! Some(convId)
    fromScreen ! GroupConversationEvent.ConversationDetails
  }

  def createConversation(): Future[ConvId] =
    for {
      z              <- zms.head
      name           <- name.head
      userIds        <- users.head
      integrationIds <- integrations.head
      shouldFullConv <- inject[GlobalPreferences].preference(ShouldCreateFullConversation).apply()
      userIds        <-
        if (userIds.isEmpty && integrationIds.isEmpty && shouldFullConv)
          z.usersStorage.values.map(
            _.filter(u => (u.isConnected || (u.teamId.isDefined && u.teamId == z.teamId)) && u.id != z.selfUserId)
              .map(_.id).toSet
              .take(ConversationController.MaxParticipants - 1)
          )
        else Future.successful(userIds)
      teamOnly       <- teamOnly.head
      readReceipts   <- if(z.teamId.isEmpty) Future.successful(false) else readReceipts.head
      _              =  verbose(l"creating conv with  ${userIds.size} users, ${integrationIds.size} bots, shouldFullConv $shouldFullConv, teamOnly $teamOnly and readReceipts $readReceipts")
      conv           <- conversationController.createGroupConversation(Name(name.trim), userIds, teamOnly, readReceipts)
      _              <- Future.sequence(integrationIds.map { case (pId, iId) => integrationsService.head.flatMap(_.addBotToConversation(conv.id, pId, iId)) })
      _              <- fromScreen.head
    } yield conv.id

  def addUsersToConversation(): Future[Unit] = {
    for {
      Some(convId)   <- convId.head
      userIds        <- users.head
      _              <- if (userIds.nonEmpty) conversationController.addMembers(convId, userIds) else Future.successful({})
      integrationIds <- integrations.head
      _              <- Future.sequence(integrationIds.map {
                          case (pId, iId) => integrationsService.head.flatMap(_.addBotToConversation(convId, pId, iId))
                        })
    } yield ()
  }
}
