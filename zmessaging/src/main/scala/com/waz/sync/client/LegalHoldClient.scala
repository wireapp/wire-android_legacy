/*
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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
import com.waz.model.{LegalHoldRequest, SyncId, TeamId, UserId}
import com.waz.utils.JsonEncoder
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import com.waz.znet2.http.Request.UrlCreator
import org.json.JSONObject

trait LegalHoldClient {
  def fetchLegalHoldRequest(teamId: TeamId,
                            userId: UserId): ErrorOrResponse[Option[LegalHoldRequest]]

  def approveRequest(teamId: TeamId,
                     userId: UserId,
                     password: Option[String]): ErrorOrResponse[Unit]
}

class LegalHoldClientImpl(implicit
                          urlCreator: UrlCreator,
                          httpClient: HttpClient,
                          authRequestInterceptor: AuthRequestInterceptor) extends LegalHoldClient {
  import LegalHoldClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  private implicit val Deserializer: RawBodyDeserializer[Option[LegalHoldRequest]] =
    RawBodyDeserializer[JSONObject].map { json =>
      if (json.has("status") && json.getString("status") == "pending") {
        Some(LegalHoldRequest.Decoder(json))
      } else {
        None
      }
    }

  override def fetchLegalHoldRequest(teamId: TeamId,
                                     userId: UserId): ErrorOrResponse[Option[LegalHoldRequest]] =
    Request.Get(relativePath = path(teamId, userId))
      .withResultType[Option[LegalHoldRequest]]()(responseDeserializerFrom(bodyDeserializerFrom(Deserializer)))
      .withErrorType[ErrorResponse]
      .executeSafe

  override def approveRequest(teamId: TeamId,
                              userId: UserId,
                              password: Option[String]): ErrorOrResponse[Unit] =
    Request.Put(
      relativePath = approvePath(teamId, userId),
      body = JsonEncoder { _.put("password", password.getOrElse("")) }
    )
    .withResultType[Unit]()
    .withErrorType[ErrorResponse]
    .executeSafe
}

object LegalHoldClient {
  def path(teamId: TeamId, userId: UserId): String = s"/teams/${teamId.str}/legalhold/${userId.str}"
  def approvePath(teamId: TeamId, userId: UserId): String = s"${path(teamId, userId)}/approve"
}
