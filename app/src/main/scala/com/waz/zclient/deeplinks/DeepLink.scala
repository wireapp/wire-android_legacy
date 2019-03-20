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
import com.waz.model.{ConvId, UserId}
import com.waz.zclient.Intents.SSOIntent
import com.waz.zclient.log.LogUI._

sealed trait DeepLink

object DeepLink extends DerivedLogTag {
  case class SSOLogin(token: String) extends DeepLink
  case class User(userId: UserId) extends DeepLink
  case class Conversation(convId: ConvId) extends DeepLink

  def apply(intent: Intent): Option[DeepLink] = Option(intent.getDataString).flatMap(apply)

  def apply(link: String): Option[DeepLink] = {
    import SSOIntent._
    verbose(l"DeepLink(${showString(link)})")
    if (link.startsWith(Prefix) && link.length > Prefix.length)
      Option(SSOLogin(link.substring(SchemeAndHost.length)))
    else None
  }
}

