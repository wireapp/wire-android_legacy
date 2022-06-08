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
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.SelfPermissions
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserPermissions.Permission._
import com.waz.model.UserPermissions._
import com.waz.model._
import com.waz.service.AccountsService.{ClientDeleted, InvalidCookie, LogoutReason, UserInitiated}
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{BuildConfig, Injectable, Injector}
import com.wire.signals.{EventStream, Signal}

import scala.concurrent.Future

class UserAccountsController(implicit injector: Injector, context: Context)
  extends Injectable with DerivedLogTag {

  import Threading.Implicits.Ui

  observeLogoutEvents()

  private lazy val zms             = inject[Signal[ZMessaging]]
  private lazy val accountsService = inject[AccountsService]
  private lazy val prefs           = inject[Signal[UserPreferences]]
  private lazy val convCtrl        = inject[ConversationController]

  lazy val accounts: Signal[Seq[AccountManager]] = accountsService.accountManagers.map {
    _.toSeq.sortBy(acc => (acc.teamId.isDefined, acc.userId.str))
  }

  val ssoToken = Signal(Option.empty[String])

  val allAccountsLoggedOut = Signal(false)
  lazy val mostRecentLoggedOutAccount = Signal[Option[(UserId, LogoutReason)]]
  lazy val onAccountLoggedOut: EventStream[(UserId, LogoutReason)] = accountsService.onAccountLoggedOut
  private var numberOfLoggedInAccounts = 0

  lazy val currentUser: Signal[Option[UserData]] = for {
    zms     <- zms
    account <- accountsService.activeAccount
    user    <- account.map(_.id).fold(Signal.const(Option.empty[UserData]))(accId => zms.usersStorage.signal(accId).map(Some(_)))
  } yield user

  lazy val teamId: Signal[Option[TeamId]] = zms.map(_.teamId)

  lazy val isTeam: Signal[Boolean] = teamId.map(_.isDefined)

  lazy val isManagedByThirdParties: Signal[Boolean] = currentUser.map(_.exists(_.isReadOnlyProfile))

  lazy val isManagedByWire = isManagedByThirdParties.map(!_)

  lazy val teamData = for {
    zms <- zms
    teamData <- zms.teams.selfTeam
  } yield teamData

  lazy val selfPermissions =
    prefs
      .flatMap(_.apply(SelfPermissions).signal)
      .map { bitmask =>
        debug(l"Self permissions bitmask: $bitmask")
        decodeBitmask(bitmask)
      }

  lazy val isAdmin: Signal[Boolean] = selfPermissions.map(AdminPermissions.subsetOf)

  lazy val isExternal: Signal[Boolean] =
    selfPermissions
      .map(ps => ExternalPermissions.subsetOf(ps) && ExternalPermissions.size == ps.size)
      .orElse(Signal.const(false))

  lazy val isWireless = zms
    .flatMap(_.users.selfUser)
    .map(_.expiresAt.isDefined)

  lazy val isProUser = isTeam.flatMap {
    case true => Signal.const(true)
    case false => isWireless
  }

  lazy val hasCreateConvPermission: Signal[Boolean] = teamId.flatMap {
    case Some(_) => selfPermissions.map(_.contains(CreateConversation))
    case  _ => Signal.const(true)
  }

  lazy val readReceiptsEnabled: Signal[Boolean] = zms.flatMap(_.propertiesService.readReceiptsEnabled)

  def hasPermissionToAddService: Future[Boolean] = {
    for {
      tId <- teamId.head
      ps  <- selfPermissions.head
    } yield tId.isDefined && ps.contains(AddConversationMember)
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
    getConversationId(user).flatMap(convCtrl.selectConv(_, ConversationChangeRequester.START_CONVERSATION))(Threading.Background)

  private def observeLogoutEvents(): Unit = {
    accounts.map(_.size).onUi { numberOfAccounts =>
      allAccountsLoggedOut ! (numberOfAccounts == 0 && numberOfLoggedInAccounts > 0)
      numberOfLoggedInAccounts = numberOfAccounts
    }

    accountsService.onAccountLoggedOut.onUi { case account @ (userId, reason) =>
      verbose(l"User $userId logged out due to $reason")

      val cookieIsInvalid = reason match {
        case InvalidCookie | ClientDeleted | UserInitiated => true
        case _ => false
      }

      (
        if (cookieIsInvalid && BuildConfig.WIPE_ON_COOKIE_INVALID) accountsService.wipeDataForAllAccounts()
        else Future.successful(())
        ).foreach{ _ =>
        mostRecentLoggedOutAccount ! Some(account)
      }
    }
  }
}
