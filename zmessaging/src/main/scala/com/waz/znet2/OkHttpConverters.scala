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
package com.waz.znet2

import java.io.{ByteArrayInputStream, InputStream}

import com.waz.utils.{IoUtils, RichOption}
import com.waz.znet2.http.HttpClient.{Progress, ProgressCallback}
import com.waz.znet2.http.Method._
import com.waz.znet2.http.{EmptyBodyImpl, Headers, RawBody, RawBodyPart}
import okhttp3.{Headers => OkHeaders, MediaType => OkMediaType, MultipartBody => OkMultipartBody, Request => OkRequest, RequestBody => OkRequestBody, Response => OkResponse}
import okio.BufferedSink

import scala.collection.JavaConverters._

object OkHttpConverters {

  def convertHttpMethod(method: http.Method): String =
    method match {
      case Get    => "GET"
      case Post   => "POST"
      case Put    => "PUT"
      case Patch  => "PATCH"
      case Delete => "DELETE"
      case Head   => "HEAD"
    }

  def convertMediaType(mediatype: String): OkMediaType =
    OkMediaType.parse(mediatype)

  def convertHeaders(headers: OkHeaders): Headers =
    Headers.create(headers.toMultimap.asScala.mapValues(_.asScala.head).toMap)

  def convertHeaders(headers: Headers): OkHeaders =
    OkHeaders.of(headers.headers.asJava)

  private def createRequestBody(body: http.Body, callback: Option[ProgressCallback], headers: Headers): Option[OkRequestBody] =
    body match {
      case _: http.EmptyBody  => None
      case body: http.RawBody => Some(createOkHttpRequestBody(callback, body, headers))
      case http.RawMultipartBody(http.MediaType.MultipartMixed, parts) =>
        Some(createOkHttpMultipartRequestBody(callback, convertMediaType(http.MediaType.MultipartMixed), parts, headers))
      case _ =>
        throw new UnsupportedOperationException("Can not create okHttp request body.")
    }

  def convertHttpRequest(request: http.Request[http.Body], callback: Option[ProgressCallback] = None): OkRequest =
    new OkRequest.Builder()
      .url(request.url)
      .method(convertHttpMethod(request.httpMethod), createRequestBody(request.body, callback, request.headers).orNull)
      .headers(convertHeaders(request.headers))
      .build()

  def convertOkHttpResponse(response: OkResponse, callback: Option[ProgressCallback]): http.Response[http.Body] =
    http.Response(
      code = response.code(),
      headers = convertHeaders(response.headers()),
      body = Option(response.body())
        .map { body =>
          val data       = body.byteStream()
          val dataLength = if (body.contentLength() == -1) None else Some(body.contentLength())
          http.RawBody(
            mediaType = Option(body.contentType()).map(_.toString),
            data = callback.map(createProgressInputStream(_, data, dataLength)).getOrElse(data),
            dataLength = dataLength
          )
        }
        .getOrElse(EmptyBodyImpl)
    )

  private def applyContentEncoding(body: RawBody, headers: Headers): RawBody =
    headers.get("Content-Encoding") match {
      case None =>
        body
      case Some(encType) if encType.equalsIgnoreCase("gzip") =>
        val zipped = IoUtils.gzip(IoUtils.toByteArray(body.data))
        body.copy(data = new ByteArrayInputStream(zipped), dataLength = Some(zipped.length))
      case _ =>
        throw new IllegalArgumentException("Unsupported content encoding.")
    }

  def createOkHttpRequestBody(callback: Option[ProgressCallback], body: http.RawBody, headers: Headers): OkRequestBody =
    new OkRequestBody() {
      private lazy val encodedBody = applyContentEncoding(body, headers)
      override val contentType: OkMediaType = encodedBody.mediaType.map(convertMediaType).orNull
      override val contentLength: Long      = encodedBody.dataLength.getOrElse(-1L)

      def writeTo(sink: BufferedSink): Unit = IoUtils.copy(
        in = callback.map(createProgressInputStream(_, encodedBody.data, encodedBody.dataLength)).getOrElse(encodedBody.data),
        out = sink.outputStream()
      )
    }

  private def createProgressInputStream(callback: ProgressCallback,
                                        data: InputStream,
                                        dataLength: Option[Long]): ProgressInputStream =
    new ProgressInputStream(
      data,
      new ProgressInputStream.Listener {
        override def progressUpdated(bytesRead: Long, totalBytesRead: Long): Unit =
          callback(Progress(totalBytesRead, dataLength))
      }
    )

  def createOkHttpMultipartRequestBody(callback: Option[ProgressCallback],
                                       mediaType: OkMediaType,
                                       parts: Seq[RawBodyPart],
                                       headers: Headers): OkRequestBody = {
    lazy val totalProgressListener = new ProgressInputStream.Listener {
      var lastProgress: Progress = Progress(0, RichOption.traverse(parts)(_.body.dataLength).map(_.sum))
      override def progressUpdated(bytesRead: Long, totalBytesRead: Long): Unit = {
        lastProgress = lastProgress.copy(progress = lastProgress.progress + bytesRead)
        callback.foreach(c => c(lastProgress))
      }
    }

    val builder = new OkMultipartBody.Builder().setType(mediaType)
    parts.foreach { part =>
      builder.addPart(
        convertHeaders(part.headers),
        new OkRequestBody {
          private lazy val encodedBody = applyContentEncoding(part.body, part.headers)
          override val contentType: OkMediaType = encodedBody.mediaType.map(convertMediaType).orNull
          override val contentLength: Long      = encodedBody.dataLength.getOrElse(-1)

          def writeTo(sink: BufferedSink): Unit = {
            val dataInputStream =
              if (callback.isEmpty) encodedBody.data
              else new ProgressInputStream(encodedBody.data, totalProgressListener)

            IoUtils.withResource(dataInputStream) { in => IoUtils.write(in, sink.outputStream()) }
          }
        }
      )
    }

    builder.build()
  }

}
