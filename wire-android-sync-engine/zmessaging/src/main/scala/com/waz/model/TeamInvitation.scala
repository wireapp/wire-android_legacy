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
package com.waz.model

import java.util.Locale

import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.utils.Locales.bcp47
import org.json.JSONObject

case class TeamInvitation(teamId: TeamId, emailAddress: EmailAddress, inviterName: String, locale: Option[Locale])

object TeamInvitation extends ((TeamId, EmailAddress, String, Option[Locale]) => TeamInvitation) {
  implicit lazy val InvitationEncoder: JsonEncoder[TeamInvitation] = new JsonEncoder[TeamInvitation] {
    override def apply(inv: TeamInvitation): JSONObject = JsonEncoder { o =>
      o.put("teamId", inv.teamId.str)
      o.put("emailAddress", inv.emailAddress.str)
      o.put("inviterName", inv.inviterName)
      inv.locale.foreach(l => o.put("locale", bcp47.languageTagOf(l)))
    }
  }

  import com.waz.utils.JsonDecoder._

  implicit lazy val InvitationDecoder: JsonDecoder[TeamInvitation] = new JsonDecoder[TeamInvitation] {
    override def apply(implicit js: JSONObject): TeamInvitation = TeamInvitation(
      decodeId[TeamId]('teamId),
      decodeEmailAddress('emailAddress),
      'inviterName,
      decodeLocale('locale))
  }
}
