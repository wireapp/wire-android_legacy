/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.deeplinks

import android.content.Intent
import com.waz.content.MembersStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.service.AccountManager.ClientRegistrationState.Registered
import com.waz.service.{AccountManager, AccountsService, UserService}
import com.wire.signals.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.deeplinks.DeepLink.{Conversation, UserTokenInfo}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{BuildConfig, Injectable, Injector}

import scala.async.Async.{async, await}
import scala.concurrent.Future

class DeepLinkService(implicit injector: Injector) extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.zclient.deeplinks.DeepLinkService.Error._
  import com.waz.zclient.deeplinks.DeepLinkService._

  val deepLink = Signal(Option.empty[CheckingResult])

  deepLink.foreach { result =>
    verbose(l"DeepLink checking result: $result")
  }

  private lazy val accountsService     = inject[AccountsService]
  private lazy val account             = inject[Signal[Option[AccountManager]]]
  private lazy val userService         = inject[Signal[UserService]]
  private lazy val convController      = inject[ConversationController]
  private lazy val membersStorage      = inject[MembersStorage]

  def checkDeepLink(intent: Intent): Unit = {
    Option(intent.getDataString) match {
      case None => deepLink ! Some(DeepLinkNotFound)
      case Some(data) if !DeepLinkParser.isDeepLink(data) => deepLink ! Some(DeepLinkNotFound)
      case Some(data) => DeepLinkParser.parseLink(data) match {
          case None =>
            deepLink ! Some(DeepLinkUnknown)
          case Some((link, rawToken)) =>
            DeepLinkParser.parseToken(link, rawToken) match {
              case None =>
                deepLink ! Some(DoNotOpenDeepLink(link, Error.InvalidToken))
              case Some(token) =>
                checkDeepLink(link, token).map {
                  deepLink ! Some(_)
                }.recover {
                  case _ => deepLink ! Some(DoNotOpenDeepLink(link, Unknown))
                }
            }
        }
    }
  }

  private def checkDeepLink(deepLink: DeepLink, token: DeepLink.Token): Future[CheckingResult] =
    async {
      val accounts = await { accountsService.accountsWithManagers.head }
      val client   = await { account.head } match {
        case None             => None
        case Some(accManager) => await { accManager.getOrRegisterClient().map(_.right.toOption) }
      }
      (accounts, client, token)
    }.flatMap {
      case (accounts, _, DeepLink.SSOLoginToken(_)) if accounts.size >= BuildConfig.MAX_ACCOUNTS =>
        Future.successful(DoNotOpenDeepLink(deepLink, SSOLoginTooManyAccounts))
      case (_, None, DeepLink.SSOLoginToken(_)) =>
        Future.successful(OpenDeepLink(token))
      case (_, Some(Registered(_)), DeepLink.SSOLoginToken(_)) =>
        Future.successful(OpenDeepLink(token))
      case (_, _, DeepLink.SSOLoginToken(_)) =>
        Future.successful(DoNotOpenDeepLink(deepLink, Unknown))
      case (_, Some(Registered(_)), DeepLink.ConversationToken(convId)) =>
        convController.getConversation(convId).map {
          case Some(_) => OpenDeepLink(token)
          case _       => DoNotOpenDeepLink(Conversation, NotFound)
        }
      case (_, _, DeepLink.ConversationToken(_)) =>
        Future.successful(DoNotOpenDeepLink(deepLink, Unknown))
      case (_, Some(Registered(_)), DeepLink.UserToken(userId)) =>
        checkUserDeepLink(deepLink, token, userId)
      case (_, _, DeepLink.UserToken(_)) =>
        Future.successful(DoNotOpenDeepLink(deepLink, Unknown))
      case (accounts, _, DeepLink.CustomBackendToken(_)) if accounts.nonEmpty =>
        Future.successful(DoNotOpenDeepLink(deepLink, UserLoggedIn))
      case (_, _, DeepLink.CustomBackendToken(_)) =>
        Future.successful(OpenDeepLink(token))
      case (_, _, DeepLink.JoinConversationToken(_, _)) =>
        Future.successful(OpenDeepLink(token))
      case _ =>
        Future.successful(OpenDeepLink(token))
    }

  private def checkUserDeepLink(deepLink: DeepLink, token: DeepLink.Token, userId: UserId) =
    async {
      val service = await { userService.head }
      await { service.syncIfNeeded(Set(userId)) }
      await { service.getSelfUser.zip(service.findUser(userId)) } match {
        case (Some(self), Some(other)) if self.id == other.id =>
          OpenDeepLink(token, UserTokenInfo(connected = false, currentTeamMember = true, self = true))
        case (Some(self), Some(other)) if self.isExternal(self.teamId, self.domain) || other.isExternal(self.teamId, self.domain) =>
          val hasConv = await { membersStorage.getActiveConvs(other.id).map(_.nonEmpty) }
          if (hasConv || self.createdBy.contains(self.id) || other.createdBy.contains(self.id))
            OpenDeepLink(token, UserTokenInfo(other.isConnected, self.isInTeam(other.teamId)))
          else
            DoNotOpenDeepLink(deepLink, NotAllowed)
        case (Some(self), Some(other)) =>
          OpenDeepLink(token, UserTokenInfo(other.isConnected, self.isInTeam(other.teamId)))
        case (Some(_), _) =>
          OpenDeepLink(token, UserTokenInfo(connected = false, currentTeamMember = false))
        case _ =>
          DoNotOpenDeepLink(deepLink, Unknown)
      }
    }
}

object DeepLinkService {

  sealed trait CheckingResult
  case object DeepLinkNotFound extends CheckingResult
  case object DeepLinkUnknown extends CheckingResult
  case class DoNotOpenDeepLink(link: DeepLink, reason: Error) extends CheckingResult
  case class OpenDeepLink(token: DeepLink.Token, additionalInfo: Any = Unit) extends CheckingResult

  sealed trait Error
  object Error {
    case object InvalidToken extends Error
    case object Unknown extends Error
    case object SSOLoginTooManyAccounts extends Error
    case object NotFound extends Error
    case object NotAllowed extends Error
    case object UserLoggedIn extends Error
  }

}
