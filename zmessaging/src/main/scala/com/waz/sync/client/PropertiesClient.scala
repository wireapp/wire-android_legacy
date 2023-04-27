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
import com.waz.model.SyncId
import com.waz.sync.client.PropertiesClient._
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._

trait PropertiesClient {
  def putProperty[T](key: String, value: T)(implicit bs: BodySerializer[T]): ErrorOrResponse[Unit]
  def getProperty[T](key: String)(implicit bd: BodyDeserializer[T]) : ErrorOrResponse[Option[T]]

  def putProperty(key: String, value: Boolean): ErrorOrResponse[Unit]
  def putProperty(key: String, value: String): ErrorOrResponse[Unit]
  def putProperty(key: String, value: Int): ErrorOrResponse[Unit]

  def getBoolean(key: String): ErrorOrResponse[Option[Boolean]]
  def getString(key: String): ErrorOrResponse[Option[String]]
  def getInt(key: String): ErrorOrResponse[Option[Int]]
}

class PropertiesClientImpl(implicit
                            urlCreator: UrlCreator,
                            httpClient: HttpClient,
                            authRequestInterceptor: AuthRequestInterceptor) extends PropertiesClient {

  import HttpClient.AutoDerivationOld._
  import HttpClient.dsl._
  import com.waz.threading.Threading.Implicits.Background

  override def putProperty[T](key: String, value: T)(implicit bs: BodySerializer[T]): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = PropertyPath(key), body = value)
      .withResultHttpCodes(ResponseCode.SuccessCodes)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getProperty[T](key: String)(implicit bd: BodyDeserializer[T]) : ErrorOrResponse[Option[T]] = {
    Request.Get(relativePath = PropertyPath(key))
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[T]()
      .withErrorType[ErrorResponse]
      .executeSafe.map {
        case Left(ErrorResponse(ResponseCode.NotFound, _, _)) => Right(None)
        case Right(v) => Right(Some(v))
        case Left(e) => Left(e)
      }
  }

  override def putProperty(key: String, value: Boolean): ErrorOrResponse[Unit] = putProperty[Boolean](key, value)
  override def putProperty(key: String, value: String): ErrorOrResponse[Unit] = putProperty[String](key, value)
  override def putProperty(key: String, value: Int): ErrorOrResponse[Unit] = putProperty[Int](key, value)

  override def getBoolean(key: String): ErrorOrResponse[Option[Boolean]] = getProperty[Boolean](key)
  override def getString(key: String): ErrorOrResponse[Option[String]] = getProperty[String](key)
  override def getInt(key: String): ErrorOrResponse[Option[Int]] = getProperty[Int](key)

}

object PropertiesClient {
  val PropertiesPath = "/properties"
  def PropertyPath(key: String) = s"/properties/$key"
}
