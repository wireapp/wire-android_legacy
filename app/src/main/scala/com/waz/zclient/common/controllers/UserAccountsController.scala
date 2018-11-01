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
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.SelfPermissions
import com.waz.model.AccountDataOld.Permission
import com.waz.model.AccountDataOld.Permission._
import com.waz.model.{AccountDataOld, _}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.utils.{ConversationSignal, UiStorage}
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class UserAccountsController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {
  import Threading.Implicits.Ui
  import UserAccountsController._

  private implicit val uiStorage = inject[UiStorage]
  val zms             = inject[Signal[ZMessaging]]
  val accountsService = inject[AccountsService]
  val prefs           = inject[Signal[UserPreferences]]

  val accounts = accountsService.accountManagers.map(_.toSeq.sortBy(acc => (acc.teamId.isDefined, acc.userId.str)))
  val convCtrl = inject[ConversationController]

  lazy val currentUser = for {
    zms     <- zms
    account <- accountsService.activeAccount
    user    <- account.map(_.id).fold(Signal.const(Option.empty[UserData]))(accId => zms.usersStorage.signal(accId).map(Some(_)))
  } yield user

  lazy val teamId: Signal[Option[TeamId]] = zms.map(_.teamId)

  lazy val isTeam: Signal[Boolean] = teamId.map(_.isDefined)

  lazy val teamData = for {
    zms <- zms
    teamData <- zms.teams.selfTeam
  } yield teamData

  lazy val selfPermissions =
    prefs
      .flatMap(_.apply(SelfPermissions).signal)
      .map(AccountDataOld.decodeBitmask)

  lazy val isAdmin: Signal[Boolean] =
    selfPermissions.map(ps => AdminPermissions.subsetOf(ps))

  lazy val hasCreateConvPermission: Signal[Boolean] = teamId.flatMap {
    case Some(_) => selfPermissions.map(_.contains(CreateConversation))
    case  _ => Signal.const(true)
  }

  def hasAddConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, AddConversationMember)

  def hasRemoveConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, RemoveConversationMember)

  private def hasConvPermission(convId: ConvId, toCheck: AccountDataOld.Permission): Signal[Boolean] = {
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
    if (conversationData.archived || conversationData.muted.isAllMuted || conversationData.hidden || conversationData.convType == ConversationData.ConversationType.Self)
      0
    else
      conversationData.unreadCount.total
  }

  lazy val unreadCount = for {
    zmsSet   <- accountsService.zmsInstances
    countMap <- Signal.sequence(zmsSet.map(z => z.convsStorage.contents.map(c => z.selfUserId -> c.values.map(unreadCountForConv).sum)).toSeq:_*)
  } yield countMap.toMap

  def getConversationId(user: UserId) =
    for {
      z    <- zms.head
      conv <- z.convsUi.getOrCreateOneToOneConversation(user)
    } yield conv.id

  def getOrCreateAndOpenConvFor(user: UserId) =
    getConversationId(user).flatMap(convCtrl.selectConv(_, ConversationChangeRequester.START_CONVERSATION))

  def hasPermissionToRemoveService(cId: ConvId): Future[Boolean] = {
    for {
      tId <- teamId.head
      ps  <- selfPermissions.head
      conv <- ConversationSignal(cId).head
    } yield tId == conv.team && ps.contains(AccountDataOld.Permission.RemoveConversationMember)
  }

  def hasPermissionToAddService: Future[Boolean] = {
    for {
      tId <- teamId.head
      ps  <- selfPermissions.head
    } yield tId.isDefined && ps.contains(AccountDataOld.Permission.AddConversationMember)
  }


}

object UserAccountsController {
  import AccountDataOld.Permission._
  val AdminPermissions: Set[Permission] = Permission.values -- Set(GetBilling, SetBilling, DeleteTeam)
}
