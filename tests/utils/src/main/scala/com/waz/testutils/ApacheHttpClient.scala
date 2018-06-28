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
package com.waz.testutils

import java.net.{URI => JURI}

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.debug
import com.waz.threading.CancellableFuture
import com.waz.utils.returning
import com.waz.utils.wrappers.JavaURI
//import com.waz.znet.AsyncClient.UserAgentHeader
//import com.waz.znet.ContentEncoder.{GzippedRequestContent, RequestContent}
//import com.waz.znet.Response.HttpStatus
import com.waz.znet._
import org.apache.http.client.fluent.{Request => ApacheRequest}
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import org.json.JSONObject

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Provides a simple HTTP client for exploring the real internet when building tests and can be used in full integration tests.
  */
//class ApacheHttpClient extends AsyncClient {
//
//  import com.waz.threading.Threading.Implicits.Background
//
//  override def apply(request: Request[_]) = {
//    debug(s"Starting request[${request.httpMethod}](${request.absoluteUri}) with body: ${request.getBody}, headers: '${request.headers}'")
//    val juri = request.absoluteUri.getOrElse(throw new IllegalArgumentException("No uri specified")).asInstanceOf[JavaURI].uri
//    CancellableFuture {
//      val resp = buildHttpRequest(juri, request.httpMethod, request.getBody, request.headers, request.followRedirect, request.timeout).execute().returnResponse()
//      val st = resp.getStatusLine
//      val body =  Try(JsonObjectResponse(new JSONObject(EntityUtils.toString(resp.getEntity)))).toOption.getOrElse(EmptyResponse)
//      val headers = Response.createHeaders(resp.getAllHeaders.map(h => h.getName -> h.getValue).toMap)
//      Response(HttpStatus(st.getStatusCode, st.getReasonPhrase), body, headers)
//    }
//  }
//
//  private def buildHttpRequest(uri: JURI, method: String, body: RequestContent, headers: Map[String, String], followRedirect: Boolean, timeout: FiniteDuration): ApacheRequest = {
//    if (method != Request.GetMethod && method != Request.PostMethod) println(s"Not handling $method methods yet")
//
//    val t = timeout.toMillis.toInt
//
//    returning((method match {
//      case Request.PostMethod => ApacheRequest.Post(uri)
//      case Request.GetMethod => ApacheRequest.Get(uri)
//      case _ => throw new IllegalArgumentException(s"Request type: $method not yet implemented")
//    }).connectTimeout(t).socketTimeout(t)) { r =>
//      headers.foreach { case (k, v) => r.setHeader(k, v.trim) }
//      r.setHeader(UserAgentHeader, "SE Tests")
//      body match {
//        case GzippedRequestContent(bytes, contentType) if contentType == "application/json" => r.bodyByteArray(bytes, ContentType.APPLICATION_JSON)
//        case _ =>
//      }
//    }
//  }
//
//  override def close(): Unit = {}
//
//  override val userAgent: String = AsyncClient.userAgent()
//
//  override val wrapper: Future[ClientWrapper] = ClientWrapper()
//}