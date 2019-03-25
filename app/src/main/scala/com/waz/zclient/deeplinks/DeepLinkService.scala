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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountManager.ClientRegistrationState.Registered
import com.waz.service.conversation.ConversationsService
import com.waz.service.{AccountManager, AccountsService, UserService}
import com.waz.utils.events.Signal
import com.waz.zclient.deeplinks.DeepLink.{Conversation, UserTokenInfo}
import com.waz.zclient.{BuildConfig, Injectable, Injector}

import scala.async.Async.{async, await}
import scala.concurrent.Future

class DeepLinkService(implicit injector: Injector) extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.zclient.deeplinks.DeepLinkService.Error._
  import com.waz.zclient.deeplinks.DeepLinkService._

  val deepLink = Signal(Option.empty[CheckingResult])

  private lazy val accountsService     = inject[AccountsService]
  private lazy val account             = inject[Signal[Option[AccountManager]]]
  private lazy val conversationService = inject[Signal[ConversationsService]]
  private lazy val userService         = inject[Signal[UserService]]

  def checkDeepLink(intent: Intent): Unit =
    Option(intent.getDataString).flatMap(DeepLinkParser.parseLink) match {
      case None => deepLink ! None
      case Some((link, rawToken)) =>
        DeepLinkParser.parseToken(link, rawToken) match {
          case None => deepLink ! Some(DoNotOpenDeepLink(link, Error.InvalidToken))
          case Some(token) =>
            checkDeepLink(link, token).map { deepLink ! Some(_) }.recover {
              case _ => deepLink ! Some(DoNotOpenDeepLink(link, Unknown))
            }
        }
    }

  private def checkDeepLink(deepLink: DeepLink, token: DeepLink.Token): Future[CheckingResult] =
    token match {
      case DeepLink.SSOLoginToken(_) =>
        async {
          val accounts = await { accountsService.accountsWithManagers.head }
          val acc = await { account.head }
          if (accounts.size >= BuildConfig.MAX_ACCOUNTS)
            DoNotOpenDeepLink(deepLink, SSOLoginTooManyAccounts)
          else if (acc.isEmpty)
            OpenDeepLink(token)
          else {
            await { acc.get.getOrRegisterClient() } match {
              case Right(Registered(_)) => OpenDeepLink(token)
              case _ => DoNotOpenDeepLink(deepLink, Unknown)
            }
          }
        }

      case DeepLink.ConversationToken(convId) =>
        async {
          val convService = conversationService.currentValue
          if (convService.isEmpty) DoNotOpenDeepLink(Conversation, Unknown)
          else {
            val conv = await { convService.get.content.convById(convId) }
            if (conv.isEmpty) DoNotOpenDeepLink(Conversation, NotFound)
            else OpenDeepLink(token)
          }
        }

      case DeepLink.UserToken(userId) =>
        async {
          val service = await { userService.head }
          await { service.syncIfNeeded(Set(userId)) }
          await { service.getSelfUser.zip(service.findUser(userId))} match {
            case (Some(self), Some(other)) =>
              OpenDeepLink(token, UserTokenInfo(other.isConnected, self.isInTeam(other.teamId)))
            case (Some(_), _) =>
              OpenDeepLink(token, UserTokenInfo(connected = false, currentTeamMember = false))
            case _ =>
              DoNotOpenDeepLink(deepLink, Unknown)
          }
        }

      case _ =>
        Future.successful(OpenDeepLink(token))
    }
}

object DeepLinkService {

  sealed trait CheckingResult
  case object DeepLinkNotFound extends CheckingResult
  case class DoNotOpenDeepLink(link: DeepLink, reason: Error) extends CheckingResult
  case class OpenDeepLink(token: DeepLink.Token, additionalInfo: Any = Unit) extends CheckingResult

  sealed trait Error
  object Error {
    case object InvalidToken extends Error
    case object Unknown extends Error
    case object SSOLoginTooManyAccounts extends Error
    case object NotFound extends Error
  }

}
