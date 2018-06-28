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
package com.waz.znet2.http

import java.io.InputStream
import java.net.URL
import java.util.Locale

import com.waz.threading.CancellableFuture
import com.waz.utils.wrappers.URI

import scala.concurrent.ExecutionContext

object MediaType {
  val Json              = "application/json"
  val Bytes             = "application/octet-stream"
  val PlainText         = "text/plain"
  val Protobuf          = "application/x-protobuf"
  val MultipartFormData = "multipart/form-data"
  val MultipartMixed    = "multipart/mixed"
}

sealed trait Method
object Method {
  case object Get    extends Method
  case object Post   extends Method
  case object Put    extends Method
  case object Delete extends Method
  case object Patch  extends Method
  case object Head   extends Method
}

object ResponseCode {
  val Success             = 200
  val Created             = 201
  val Accepted            = 202
  val NoResponse          = 204
  val MovedPermanently    = 301
  val MovedTemporarily    = 302
  val SeeOther            = 303
  val BadRequest          = 400
  val Unauthorized        = 401
  val Forbidden           = 403
  val NotFound            = 404
  val RateLimiting        = 420
  val LoginRateLimiting   = 429
  val Conflict            = 409
  val PreconditionFailed  = 412
  val InternalServerError = 500

  val SuccessCodes: Set[Int]           = Set(Success, Created, Accepted, NoResponse)
  def isSuccessful(code: Int): Boolean = SuccessCodes.contains(code)
  def isFatal(code: Int): Boolean =
    code != Unauthorized && code != RateLimiting && code / 100 == 4
}

class Headers private (val headers: Map[String, String]) {
  require(headers.keys.forall(key => Headers.toLower(key) == key))

  lazy val isEmpty: Boolean = headers.isEmpty

  def get(key: String): Option[String] = headers.get(Headers.toLower(key))
  def foreach(key: String)(f: String => Unit): Unit = get(key).foreach(f)
  def delete(key: String): Headers = new Headers(headers - key)
  def add(keyValue: (String, String)): Headers = new Headers(headers + keyValue)

  override def toString: String = s"Headers[$headers]"
}

object Headers {
  val empty                                      = Headers(Map.empty[String, String])
  def apply(entries: (String, String)*): Headers = apply(entries.toMap)
  def apply(headers: Map[String, String]): Headers = new Headers(headers.map { case (key, value) => toLower(key) -> value })
  private def toLower(key: String) = key.toLowerCase(Locale.US)
}

sealed trait Body

sealed trait EmptyBody extends Body
object EmptyBodyImpl   extends EmptyBody

case class RawBody(mediaType: Option[String], data: InputStream, dataLength: Option[Long] = None) extends Body

case class RawMultipartBodyMixed(parts: Seq[RawMultipartBodyMixed.Part]) extends Body
object RawMultipartBodyMixed {
  case class Part(body: RawBody, headers: Headers)
}

case class RawMultipartBodyFormData(parts: Seq[RawMultipartBodyFormData.Part]) extends Body
object RawMultipartBodyFormData {
  case class Part(body: RawBody, name: String, fileName: Option[String])
}


//TODO Maybe it would be better to move this models into http client dsl
case class MultipartBodyMixed(parts: List[MultipartBodyMixed.Part[_]])
object MultipartBodyMixed {
  def apply(parts: MultipartBodyMixed.Part[_]*): MultipartBodyMixed = new MultipartBodyMixed(parts.toList)
  case class Part[T](body: T, headers: Headers = Headers.empty)(implicit val serializer: RawBodySerializer[T]) {
    def serialize: RawBody = serializer.serialize(body)
  }
}

case class MultipartBodyFormData(parts: List[MultipartBodyFormData.Part[_]])
object MultipartBodyFormData {
  def apply(parts: MultipartBodyFormData.Part[_]*): MultipartBodyFormData = new MultipartBodyFormData(parts.toList)
  case class Part[T](body: T, name: String, fileName: Option[String] = None)(implicit val serializer: RawBodySerializer[T]) {
    def serialize: RawBody = serializer.serialize(body)
  }
}

object Request {

  type QueryParameter = (String, String)

  def Head[T](
      url: URL,
      queryParameters: List[QueryParameter] = List.empty,
      headers: Headers = Headers.empty,
      body: T = EmptyBodyImpl: EmptyBody
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[T] =
    create(Method.Head, url, queryParameters, headers, body)

  def Get(
      url: URL,
      queryParameters: List[QueryParameter] = List.empty,
      headers: Headers = Headers.empty
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[EmptyBody] =
    create(Method.Get, url, queryParameters, headers, EmptyBodyImpl)

  def Post[T](
      url: URL,
      queryParameters: List[QueryParameter] = List.empty,
      headers: Headers = Headers.empty,
      body: T = EmptyBodyImpl: EmptyBody
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[T] =
    create(Method.Post, url, queryParameters, headers, body)

  def Put[T](
      url: URL,
      queryParameters: List[QueryParameter] = List.empty,
      headers: Headers = Headers.empty,
      body: T = EmptyBodyImpl: EmptyBody
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[T] =
    create(Method.Put, url, queryParameters, headers, body)

  def Delete[T](
      url: URL,
      queryParameters: List[QueryParameter] = List.empty,
      headers: Headers = Headers.empty,
      body: T = EmptyBodyImpl: EmptyBody
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[T] =
    create(Method.Delete, url, queryParameters, headers, body)

  private def create[T](
      method: Method,
      url: URL,
      queryParameters: List[QueryParameter],
      headers: Headers,
      body: T
  )(implicit interceptor: RequestInterceptor = RequestInterceptor.identity): Request[T] = {
    //TODO Refactor this conversions
    val urlWithParameters = new URL(
      queryParameters
        .foldLeft(URI.parse(url.toString).buildUpon) {
          case (builder, (key, value)) =>
            builder.appendQueryParameter(key, value)
        }
        .build
        .toString
    )
    new Request(urlWithParameters, method, headers, body, interceptor)
  }

}

trait RequestInterceptor { self =>
  def intercept(request: Request[Body]): CancellableFuture[Request[Body]]
  def andThen(that: RequestInterceptor)(implicit ec: ExecutionContext): RequestInterceptor =
    RequestInterceptor.compose(this, that)
}

object RequestInterceptor {

  val identity: RequestInterceptor = new RequestInterceptor {
    override def intercept(request: Request[Body]): CancellableFuture[Request[Body]] =
      CancellableFuture.successful(request)
  }

  def compose(a: RequestInterceptor, b: RequestInterceptor)(implicit ec: ExecutionContext): RequestInterceptor =
    new RequestInterceptor {
      override def intercept(request: Request[Body]): CancellableFuture[Request[Body]] =
        a.intercept(request).flatMap(b.intercept)
    }

}

case class Request[+T](url: URL, httpMethod: Method, headers: Headers, body: T, interceptor: RequestInterceptor)

case class Response[T](code: Int, headers: Headers, body: T)
