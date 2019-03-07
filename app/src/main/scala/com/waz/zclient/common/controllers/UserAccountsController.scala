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
import com.waz.ZLog._
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.SelfPermissions
import com.waz.model.UserPermissions.Permission._
import com.waz.model.UserPermissions._
import com.waz.model._
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

  private implicit val uiStorage   = inject[UiStorage]
  private lazy val zms             = inject[Signal[ZMessaging]]
  private lazy val accountsService = inject[AccountsService]
  private lazy val prefs           = inject[Signal[UserPreferences]]
  private lazy val convCtrl        = inject[ConversationController]

  lazy val accounts = accountsService.accountManagers.map(_.toSeq.sortBy(acc => (acc.teamId.isDefined, acc.userId.str)))

  private var numberOfLoggedInAccounts = 0

  val onAllLoggedOut = Signal(false)

  accounts.map(_.size).onUi { accsNumber =>
    onAllLoggedOut ! (accsNumber == 0 && numberOfLoggedInAccounts > 0)
    numberOfLoggedInAccounts = accsNumber
  }

  val ssoToken = Signal(Option.empty[String])

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
      .map { bitmask =>
        debug(s"Self permissions bitmask: $bitmask")
        decodeBitmask(bitmask)
      }

  lazy val isAdmin: Signal[Boolean] =
    selfPermissions.map(AdminPermissions.subsetOf)

  lazy val isPartner: Signal[Boolean] =
    selfPermissions
      .map(ps => PartnerPermissions.subsetOf(ps) && PartnerPermissions.size == ps.size)
      .orElse(Signal.const(false))

  lazy val hasCreateConvPermission: Signal[Boolean] = teamId.flatMap {
    case Some(_) => selfPermissions.map(_.contains(CreateConversation))
    case  _ => Signal.const(true)
  }

  lazy val hasChangeGroupSettingsPermission: Signal[Boolean] =
    isPartner.map(!_)

  lazy val readReceiptsEnabled: Signal[Boolean] = zms.flatMap(_.propertiesService.readReceiptsEnabled)

  def hasAddConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, AddConversationMember)

  def hasRemoveConversationMemberPermission(convId: ConvId): Signal[Boolean] =
    hasConvPermission(convId, RemoveConversationMember)

  private def hasConvPermission(convId: ConvId, toCheck: Permission): Signal[Boolean] = {
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
    } yield tId == conv.team && ps.contains(RemoveConversationMember)
  }

  def hasPermissionToAddService: Future[Boolean] = {
    for {
      tId <- teamId.head
      ps  <- selfPermissions.head
    } yield tId.isDefined && ps.contains(AddConversationMember)
  }


}
