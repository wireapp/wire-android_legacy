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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.SearchQuery.{Recommended, RecommendedHandle, TopPeople}
import com.waz.model._
import com.waz.sync.client.UserSearchClient.{DefaultLimit, UserSearchResponse}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.CirceJSONSupport
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._

trait UserSearchClient {
  def getContacts(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[UserSearchResponse]
  def exactMatchHandle(handle: Handle): ErrorOrResponse[Option[UserId]]
}

class UserSearchClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends UserSearchClient with CirceJSONSupport {
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import Threading.Implicits.Background
  import UserSearchClient._

  private implicit val errorResponseDeserializer: RawBodyDeserializer[ErrorResponse] =
    objectFromCirceJsonRawBodyDeserializer[ErrorResponse]

  override def getContacts(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[UserSearchResponse] = {
    debug(l"graphSearch('$query', $limit)")

    //TODO Get rid of this
    if (query.isInstanceOf[TopPeople.type]) {
      warn(l"A request to /search/top was made - this is now only handled locally")
      CancellableFuture.successful(Right(Seq.empty))
    }

    val prefix = (query: @unchecked) match {
      case Recommended(p)        => p
      case RecommendedHandle(p)  => p
    }

    Request
      .Get(
        relativePath = ContactsPath,
        queryParameters = queryParameters("q" -> prefix, "size" -> limit, "l" -> Relation.Third.id, "d" -> 1)
      )
      .withResultType[UserSearchResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }


  override def exactMatchHandle(handle: Handle): ErrorOrResponse[Option[UserId]] = {
    Request.Get(relativePath = handlesQuery(handle))
      .withResultType[ExactHandleResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map {
        case Right(response) => Right(Some(UserId(response.user)))
        case Left(response) if response.code == ResponseCode.NotFound => Right(None)
        case Left(response) => Left(response)
      }
  }
}

object UserSearchClient extends DerivedLogTag {
  val ContactsPath = "/search/contacts"
  val HandlesPath = "/users/handles"

  val DefaultLimit = 10

  def handlesQuery(handle: Handle): String =
    UserSearchClient.HandlesPath + "/" + Handle.stripSymbol(handle.string)

  // Response types

  case class ExactHandleResponse(user: String)
  case class UserSearchResponse(documents: Seq[UserSearchResponse.User])

  object UserSearchResponse {
    case class User(id: String, name: String, handle: String, accent_id: Option[Int])
  }
}
