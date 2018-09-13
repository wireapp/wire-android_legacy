/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import java.util.UUID

import com.waz.sync.client.{ErrorOr, LoginClient}

import scala.util.Try
import scala.util.matching.Regex

object SSOService {

  val Prefix = "wire-"
  val UUIDRegex: Regex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}".r
  val TokenRegex: Regex = (Prefix + UUIDRegex.regex).r

}

class SSOService(val loginClient: LoginClient) {
  import SSOService._

  def extractToken(string: String): Option[String] = TokenRegex.findFirstIn(string)

  def isTokenValid(token: String): Boolean = TokenRegex.pattern.matcher(token).matches()

  def extractUUID(token: String): Option[UUID] = Try{ UUID.fromString(token.drop(Prefix.length)) }.toOption

  def verifyToken(token: UUID): ErrorOr[Boolean] = loginClient.verifySSOToken(token)

}
