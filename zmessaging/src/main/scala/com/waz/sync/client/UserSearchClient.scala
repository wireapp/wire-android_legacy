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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.model.SearchQuery.{Recommended, RecommendedHandle, TopPeople}
import com.waz.model._
import com.waz.sync.client.UserSearchClient.{DefaultLimit, UserSearchEntry}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.JsonDecoder
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json.JSONObject

import scala.util.control.NonFatal

trait UserSearchClient {
  def getContacts(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[Seq[UserSearchEntry]]
  def exactMatchHandle(handle: Handle): ErrorOrResponse[Option[UserId]]
}

class UserSearchClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends UserSearchClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import Threading.Implicits.Background
  import UserSearchClient._

  private implicit val UsersSearchDeserializer: RawBodyDeserializer[Seq[UserSearchEntry]] =
    RawBodyDeserializer[JSONObject].map(json => UserSearchResponse.unapply(JsonObjectResponse(json)).get)

  override def getContacts(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[Seq[UserSearchEntry]] = {
    debug(s"graphSearch('$query', $limit)")

    //TODO Get rid of this
    if (query.isInstanceOf[TopPeople.type]) {
      warn("A request to /search/top was made - this is now only handled locally")
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
      .withResultType[Seq[UserSearchEntry]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  private implicit val UserIdDeserializer: RawBodyDeserializer[UserId] =
    RawBodyDeserializer[JSONObject].map(json => UserId(json.getString("user")))

  override def exactMatchHandle(handle: Handle): ErrorOrResponse[Option[UserId]] = {
    Request.Get(relativePath = handlesQuery(handle))
      .withResultType[UserId]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map {
        case Right(userId) => Right(Some(userId))
        case Left(response) if response.code == ResponseCode.NotFound => Right(None)
        case Left(response) => Left(response)
      }
  }
}

object UserSearchClient {
  val ContactsPath = "/search/contacts"
  val HandlesPath = "/users/handles"

  val DefaultLimit = 10

  def handlesQuery(handle: Handle): String =
    UserSearchClient.HandlesPath + "/" + Handle.stripSymbol(handle.string)

  case class UserSearchEntry(id: UserId, name: String, colorId: Option[Int], handle: Handle)

  object UserSearchEntry {
    import JsonDecoder._

    implicit lazy val Decoder: JsonDecoder[Option[UserSearchEntry]] = new JsonDecoder[Option[UserSearchEntry]] {
      override def apply(implicit js: JSONObject): Option[UserSearchEntry] =
        if (js.has("handle")) Some(UserSearchEntry('id, 'name, 'accent_id, 'handle)) else None
    }
  }

  object UserSearchResponse {

    def unapply(resp: ResponseContent): Option[Seq[UserSearchEntry]] = resp match {
      case JsonObjectResponse(js) if js.has("documents") =>
        try {
          Some(JsonDecoder.array[Option[UserSearchEntry]](js.getJSONArray("documents")).flatten)
        } catch {
          case NonFatal(ex) => warn(s"parsing failed", ex)
            None
        }
      case _ => None
    }

  }

  object ExactMatchHandleResponseContent {
    def unapply(response: ResponseContent): Option[UserId] = response match {
      case JsonObjectResponse(js) if js.has("user") => Some(UserId(js.getString("user")))
      case _ => None
    }
  }

}
