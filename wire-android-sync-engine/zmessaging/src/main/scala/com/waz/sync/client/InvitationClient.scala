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

import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.BackendConfig
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.waz.utils.Locales.{bcp47, currentLocale}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}
import org.json.JSONObject
import org.threeten.bp.Instant

trait InvitationClient {
  def postTeamInvitation(invitation: TeamInvitation): ErrorOrResponse[ConfirmedTeamInvitation]
}

class InvitationClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends InvitationClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import com.waz.sync.client.InvitationClient._

  override def postTeamInvitation(invitation: TeamInvitation): ErrorOrResponse[ConfirmedTeamInvitation] = {
    Request.Post(relativePath = teamInvitationPath(invitation.teamId), body = invitation)
      .withResultType[ConfirmedTeamInvitation]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object InvitationClient {
  def teamInvitationPath(teamId: TeamId) = s"/teams/$teamId/invitations"

  implicit lazy val TeamInviteEncoder: JsonEncoder[TeamInvitation] = new JsonEncoder[TeamInvitation] {
    def apply(i: TeamInvitation): JSONObject = JsonEncoder { js =>
      js.put("email", i.emailAddress)
      js.put("inviter_name", i.inviterName)
      js.put("locale", bcp47.languageTagOf(i.locale.getOrElse(currentLocale)))
    }
  }

  case class ConfirmedTeamInvitation(id: InvitationId, emailAddress: EmailAddress, createdAt: Instant, teamId:TeamId)

  implicit lazy val TeamInviteDecoder: JsonDecoder[ConfirmedTeamInvitation] = JsonDecoder.lift { implicit js =>
    import JsonDecoder._
    ConfirmedTeamInvitation('id, 'email, Instant.parse(js.getString("created_at")), 'team)
  }

}
