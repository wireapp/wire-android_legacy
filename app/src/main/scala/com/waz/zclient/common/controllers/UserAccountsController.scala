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
import com.waz.ZLog.ImplicitTag._
import com.waz.model.AccountData.Permission._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.{Injectable, Injector}

class UserAccountsController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  import Threading.Implicits.Ui

  val zms = inject[Signal[ZMessaging]]

  val accounts = Option(ZMessaging.currentAccounts).fold(Signal.const(Seq.empty[AccountData]))(_.loggedInAccounts.map(_.toSeq.sortBy(acc => (acc.isTeamAccount, acc.id.str))))
  val convCtrl = inject[ConversationController]

  lazy val currentUser = for {
    zms     <- zms
    account <- ZMessaging.currentAccounts.activeAccount
    user    <- account.flatMap(_.userId).fold(Signal.const(Option.empty[UserData]))(accId => zms.usersStorage.signal(accId).map(Some(_)))
  } yield user

  lazy val teamId: Signal[Option[TeamId]] = zms.map(_.teamId)

  lazy val isTeam: Signal[Boolean] = teamId.map(_.isDefined)

  lazy val teamData = for {
    zms <- zms
    teamData <- zms.teams.selfTeam
  } yield teamData

  lazy val selfPermissions = for {
    zms <- zms
    accountData <- zms.account.accountData
  } yield accountData.selfPermissions

  lazy val hasCreateConvPermission: Signal[Boolean] =
    selfPermissions.map(_.contains(CreateConversation))

  def hasAddConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, AddConversationMember)

  def hasRemoveConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, RemoveConversationMember)

  private def hasConvPermission(convId: ConvId, toCheck: AccountData.Permission): Signal[Boolean] = {
    for {
      z    <- zms
      conv <- z.convsStorage.signal(convId)
      ps   <- selfPermissions
    } yield
      conv.team.isEmpty || (conv.team == z.teamId && ps(toCheck))
  }

  def isTeamMember(userId: UserId) =
    for {
      z    <- zms
      user <- z.usersStorage.signal(userId)
    } yield z.teamId.isDefined && z.teamId == user.teamId

  private def unreadCountForConv(conversationData: ConversationData): Int = {
    if (conversationData.archived || conversationData.muted || conversationData.hidden || conversationData.convType == ConversationData.ConversationType.Self)
      0
    else
      conversationData.unreadCount.total
  }

  lazy val unreadCount = for {
    zmsSet   <- ZMessaging.currentAccounts.zmsInstances
    countMap <- Signal.sequence(zmsSet.map(z => z.convsStorage.convsSignal.map(c => z.accountId -> c.conversations.map(unreadCountForConv).sum)).toSeq:_*)
  } yield countMap.toMap

  def getConversationId(user: UserId) =
    for {
      z    <- zms.head
      conv <- z.convsUi.getOrCreateOneToOneConversation(user)
    } yield conv.id

  def getOrCreateAndOpenConvFor(user: UserId) =
    getConversationId(user).flatMap(convCtrl.selectConv(_, ConversationChangeRequester.START_CONVERSATION))

  //TODO remove when SendConnectRequestFragment is rewritten
  def hasRemoveConversationMemberPermissionJava(convId: ConvId): Boolean =
    hasRemoveConversationMemberPermission(convId).currentValue.getOrElse(false)

}
