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
package com.waz.sync.client

import com.waz.model._
import com.waz.threading.CancellableFuture.successful
import com.waz.threading.Threading
import com.waz.utils.JsonEncoder
import com.waz.utils.Locales.{bcp47, currentLocale}
import com.waz.znet.ContentEncoder.json
import com.waz.znet.Response.HttpStatus
import com.waz.znet.Response.Status.Created
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.util.Try

class InvitationClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.InvitationClient._

  def postTeamInvitation(invitation: TeamInvitation): ErrorOrResponse[ConfirmedTeamInvitation] =
    netClient.chainedWithErrorHandling("postTeamInvitation", Request.Post(teamInvitationPath(invitation.teamId), invitation)(json(EncodeTeamInvite))) {
      case Response(HttpStatus(Created, _), ConfirmedTeamInvitation(inv), _) =>
        successful(Right(inv))
    }
}

object InvitationClient {
  def teamInvitationPath(teamId: TeamId) = s"teams/$teamId/invitations"

  val RedirectToUser = "(?:/self)?/connections/([^/]+)".r

  implicit lazy val EncodeTeamInvite: JsonEncoder[TeamInvitation] = new JsonEncoder[TeamInvitation] {
    def apply(i: TeamInvitation): JSONObject = JsonEncoder { js =>
      js.put("email", i.emailAddress)
      js.put("inviter_name", i.inviterName)
      js.put("locale", bcp47.languageTagOf(i.locale.getOrElse(currentLocale)))
    }
  }

  case class ConfirmedTeamInvitation(id: InvitationId, emailAddress: EmailAddress, createdAt: Instant, teamId:TeamId)
  object ConfirmedTeamInvitation {
    import com.waz.utils.JsonDecoder._
    def unapply(resp: ResponseContent): Option[ConfirmedTeamInvitation] = resp match {
      case JsonObjectResponse(js) =>
        implicit val jObject: JSONObject = js
        Try(ConfirmedTeamInvitation('id, 'email, Instant.parse(js.getString("created_at")), 'team)).toOption
      case _ => None
    }
  }
}
