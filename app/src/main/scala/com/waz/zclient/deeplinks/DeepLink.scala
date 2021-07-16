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

import java.net.{URI, URL}

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.zclient.BuildConfig
import com.waz.zclient.log.LogUI._
import com.waz.utils.wrappers.{URI => WireURI}

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

sealed trait DeepLink

object DeepLink extends DerivedLogTag {
  case object SSOLogin extends DeepLink
  case object User extends DeepLink
  case object Conversation extends DeepLink
  case object JoinConversation extends DeepLink
  case object Access extends DeepLink

  sealed trait Token
  case class SSOLoginToken(token: String) extends Token
  case class UserToken(userId: UserId) extends Token
  case class ConversationToken(conId: ConvId) extends Token
  case class JoinConversationToken(key: String, code: String) extends Token
  case class CustomBackendToken(url: URL) extends Token

  case class UserTokenInfo(connected: Boolean, currentTeamMember: Boolean, self: Boolean = false)

  case class RawToken(value: String) extends AnyVal

  def getAll: Seq[DeepLink] = Seq(SSOLogin, User, JoinConversation, Conversation, Access)
}

object DeepLinkParser {
  import DeepLink._

  private val Scheme = BuildConfig.CUSTOM_URL_SCHEME
  private val UuidRegex: Regex =
    "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}".r

  def hostBy(link: DeepLink): String = link match {
    case DeepLink.SSOLogin => "start-sso"
    case DeepLink.User => "user"
    case DeepLink.Conversation => "conversation"
    case DeepLink.JoinConversation => "conversation-join"
    case DeepLink.Access => "access"
  }

  def isDeepLink(str: String): Boolean = str.startsWith(s"$Scheme://")

  def parseLink(str: String): Option[(DeepLink, RawToken)] =
    getAll.view
      .map { link =>
        val prefix = s"$Scheme://${hostBy(link)}"
        if (str.length > prefix.length && str.startsWith(prefix))
          Some(link -> rawToken(link, str, prefix))
        else
          None
      }
      .collectFirst { case Some(res) => res }

  private def rawToken(link: DeepLink, str: String, prefix: String): RawToken = link match {
    case JoinConversation =>
      RawToken(str)
    case _ =>
      val tokenStr = str.substring(prefix.length).trim
      RawToken(
        if (tokenStr.startsWith("/?") || tokenStr.startsWith("/"))
          tokenStr.substring(1)
        else
          tokenStr
      )
  }

  def parseToken(link: DeepLink, raw: RawToken): Option[Token] = link match {
    case SSOLogin =>
      val tokenRegex = s"wire-${UuidRegex.regex}".r
      for {
        _ <- tokenRegex.findFirstIn(raw.value)
      } yield SSOLoginToken(raw.value)

    case DeepLink.User =>
      for {
        res <- UuidRegex.findFirstIn(raw.value)
        userId = UserId(res)
      } yield UserToken(userId)

    case DeepLink.Conversation =>
      for {
        res <- UuidRegex.findFirstIn(raw.value)
        convId = ConvId(res)
      } yield ConversationToken(convId)

    case DeepLink.JoinConversation =>
      Try(WireURI.parse(raw.value)) match {
        case Failure(exception) =>
          warn(l"Couldn't parse join conversation deep link.", exception)
          None

        case Success(uri) =>
          val key = uri.getQueryParameter("key")
          val code = uri.getQueryParameter("code")
          (Option(key), Option(code)) match {
            case (Some(keyStr), Some(codeStr)) => Some(JoinConversationToken(key = keyStr, code = codeStr))
            case _ =>
              warn(l"Couldn't parse join conversation queries (key = $key, code = $code)")
              None
          }
      }

    case DeepLink.Access =>
      Try(new URI(raw.value)) match {
        case Failure(exception) =>
          warn(l"Couldn't parse access token.", exception)
          None

        case Success(uri) =>
          val query = uri.getQuery
          if (query.startsWith("config=")) {
            val configAddress = query.stripPrefix("config=")

            Try(new URL(configAddress)) match {
              case Failure(exception) =>
                warn(l"Couldn't parse access token query.", exception)
                None

              case Success(url) =>
                Some(CustomBackendToken(url))
            }
          } else {
            warn(l"Couldn't find access token query parameter.")
            None
          }
      }
  }

}
