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

import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import okhttp3._
import okio.Buffer
import com.waz.znet2
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

/**
  * Logs request and response lines and their respective headers and bodies (if present).
  * For logging bodies we need to cache whole body content in memory. So, be careful!
  * Based on: https://github.com/square/okhttp/tree/master/okhttp-logging-interceptor
  *
  * @param logBodyTypes Content types for which logger will print the bodies.
  *                     Be careful with this and try to not specify content types with potentially large bodies.
  *
  * Example:
  *
  * --> POST /greeting http/1.1
  * Host: example.com
  * Content-Type: plain/text
  * Content-Length: 3
  *
  * Hi?
  *
  * --> END POST
  *
  * <-- 200 OK (22ms)
  * Content-Type: plain/text
  * Content-Length: 6
  *
  * Hello!
  *
  * <-- END HTTP
  */
final class OkHttpLoggingInterceptor(logBodyTypes: List[String], maxBodyStringLength: Int = 50000)
  extends Interceptor with DerivedLogTag {

  private val CharsetUtf8: Charset = Charset.forName("UTF-8")
  private val truncatedBodySuffix: String = "...TRUNCATED"

  private def shouldLogBody(bodyMediaType: MediaType): Boolean = {
    val mediaTypeStr = bodyMediaType.toString.toLowerCase()
    logBodyTypes.exists(logType => mediaTypeStr.contains(logType.toLowerCase))
  }

  //TODO This is a hack. Think about more general and flexible solution
  private def hideSensitiveInfo(body: String, mediaType: Option[MediaType]): String = {
    val mediaTypeStr = mediaType.map(_.toString).getOrElse("")
    val isJson = mediaTypeStr.toLowerCase.contains(znet2.http.MediaType.Json.toLowerCase)
    (if (isJson) Try(new JSONObject(body)).toOption else None)
      .map { json =>
        if (json.optString("password").nonEmpty) json.put("password", "*hidden*")
        else json
      }
      .map(_.toString)
      .getOrElse(body)
  }

  private def prepareBodyForLogging(body: Array[Byte], mediaType: Option[MediaType]): String = {
    val bodyStrSafe = hideSensitiveInfo(new String(body, CharsetUtf8), mediaType)
    if (bodyStrSafe.length <= maxBodyStringLength) bodyStrSafe
    else bodyStrSafe.subSequence(0, (maxBodyStringLength - truncatedBodySuffix.length) max 1) + truncatedBodySuffix
  }

  @throws[IOException]
  override def intercept(chain: Interceptor.Chain): Response = {
    val logMsgBuilder = new StringBuilder("\n")

    var request = chain.request
    val requestBody = Option(request.body)
    val logRequestBody = requestBody.flatMap(body => Option(body.contentType)).fold(false)(shouldLogBody)

    val connection = chain.connection
    val requestStartMessage = s"--> ${request.method} ${request.url}${Option(connection).fold("")(" " + _.protocol)}"
    logMsgBuilder.append(s"$requestStartMessage\n")

    requestBody.foreach { body =>
      // Request body headers are only present when installed as a network interceptor. Force
      // them to be included (when available) so there values are known.
      if (body.contentType != null) logMsgBuilder.append(s"Content-Type: ${body.contentType}\n")
      if (body.contentLength != -1) logMsgBuilder.append(s"Content-Length: ${body.contentLength}\n")
    }

    // Skip headers from the request body as they are explicitly logged above.
    val skipHeaders = List("Content-Type", "Content-Length")
    request.headers().names().asScala
      .filterNot(name => skipHeaders.exists(_.equalsIgnoreCase(name)))
      .foreach { name =>
        logMsgBuilder.append(s"$name: ${request.headers.get(name)}\n")
      }

    if (!logRequestBody)
      logMsgBuilder.append(s"--> END ${request.method} ${requestBody.fold("")("(" + _.contentLength() + "-byte body)")}\n")
    else requestBody.foreach { body =>
      val buffer = new Buffer()
      body.writeTo(buffer)

      //Read all content into byte array. It may be dangerous for large bodies!!!
      val bodyBytes = buffer.readByteArray()
      //Override request body. We have to do this for body logging
      request = request
        .newBuilder()
        .method(request.method(), RequestBody.create(body.contentType(), bodyBytes))
        .build()

      val bodyStr = prepareBodyForLogging(bodyBytes, Option(body.contentType))
      logMsgBuilder.append(s"\n$bodyStr\n\n")
      logMsgBuilder.append("--> END " + request.method + " (" + body.contentLength + "-byte body)\n")
    }

    val startNs = System.nanoTime
    val response = Try(chain.proceed(request)).recoverWith {
      case err =>
        logMsgBuilder.append(s"<-- HTTP FAILED: $err")
        info(l"${showString(logMsgBuilder.toString)}")
        Failure(err)
    }.get
    val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - startNs)

    logMsgBuilder.append(s"<-- ${response.code} ${response.message} ${response.request.url} (${tookMs}ms)\n")
    response.headers().names().asScala
      .filterNot(name => skipHeaders.exists(_.equalsIgnoreCase(name)))
      .foreach { name =>
        logMsgBuilder.append(s"$name: ${response.headers.get(name)}\n")
      }

    val responseBody = Option(response.body)
    val logResponseBody = responseBody.flatMap(body => Option(body.contentType)).fold(false)(shouldLogBody)

    if (!logResponseBody) logMsgBuilder.append("<-- END HTTP")
    else responseBody.foreach { body =>
      val source = body.source
      source.request(Long.MaxValue)// Buffer the entire body. It may be dangerous for large bodies!!!
      val buffer = source.buffer
      //TODO What we will do with gzipped response bodies?
//      if ("gzip".equalsIgnoreCase(headers.get("Content-Encoding"))) {
//        gzippedLength = buffer.size
//        var gzippedResponseBody = null
//        try {
//          gzippedResponseBody = new Nothing(buffer.clone)
//          buffer = new Nothing
//          buffer.writeAll(gzippedResponseBody)
//        } finally if (gzippedResponseBody != null) gzippedResponseBody.close
//      }
      val bodyStr = prepareBodyForLogging(buffer.clone.readByteArray(), Option(body.contentType))
      logMsgBuilder.append(s"\n$bodyStr\n\n")
      logMsgBuilder.append(s"<-- END HTTP (${buffer.size}-byte body)")
    }

    info(l"${showString(logMsgBuilder.toString())}")
    response
  }
}
