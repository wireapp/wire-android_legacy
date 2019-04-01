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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.zclient.BuildConfig

import scala.util.matching.Regex

sealed trait DeepLink

object DeepLink extends DerivedLogTag {
  case object SSOLogin extends DeepLink
  case object User extends DeepLink
  case object Conversation extends DeepLink

  sealed trait Token
  case class SSOLoginToken(token: String) extends Token
  case class UserToken(userId: UserId) extends Token
  case class ConversationToken(conId: ConvId) extends Token

  case class UserTokenInfo(connected: Boolean, currentTeamMember: Boolean, self: Boolean = false)

  case class RawToken(value: String) extends AnyVal

  def getAll: Seq[DeepLink] = Seq(SSOLogin, User, Conversation)
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
  }

  def parseLink(str: String): Option[(DeepLink, RawToken)] = {
    getAll.view
      .map { link =>
        val prefix = s"$Scheme://${hostBy(link)}/"
        if (str.length > prefix.length && str.startsWith(prefix))
          Some(link -> RawToken(str.substring(prefix.length)))
        else
          None
      }
      .collectFirst { case Some(res) => res }
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
  }

}
