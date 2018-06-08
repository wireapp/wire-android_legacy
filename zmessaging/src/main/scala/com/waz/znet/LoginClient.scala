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
package com.waz.znet

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Credentials
import com.waz.api.impl.ErrorResponse
import com.waz.client.RegistrationClientImpl
import com.waz.model.AccountData.Label
import com.waz.model.{TeamData, TeamId, UserInfo}
import com.waz.service.BackendConfig
import com.waz.service.ZMessaging.clock
import com.waz.service.tracking.TrackingService
import com.waz.sync.client.TeamsClient.{TeamBindingResponse, teamsPaginatedQuery}
import com.waz.sync.client.UsersClient
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.{ExponentialBackoff, JsonEncoder, _}
import com.waz.znet.AuthenticationManager._
import com.waz.znet.ContentEncoder.{EmptyRequestContent, JsonContentEncoder}
import com.waz.znet.LoginClient.LoginResult
import com.waz.znet.Response.{Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient.ErrorOr
import org.json.JSONObject
import org.threeten.bp

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait LoginClient {
  def access(cookie: Cookie, token: Option[AccessToken]): CancellableFuture[LoginResult]
  def login(credentials: Credentials): CancellableFuture[LoginResult]
  def getSelfUserInfo(token: AccessToken): ErrorOr[UserInfo]

  def findSelfTeam(accessToken: AccessToken, start: Option[TeamId] = None): ErrorOr[Option[TeamData]]
  def getTeams(accessToken: AccessToken, start: Option[TeamId]): ErrorOr[TeamBindingResponse]
}

class LoginClientImpl(client: AsyncClient, backend: BackendConfig, tracking: TrackingService) extends LoginClient {
  import com.waz.znet.LoginClient._
  private implicit val dispatcher = new SerialDispatchQueue(name = "LoginClient")

  private[znet] var lastRequestTime = 0L
  private[znet] var failedAttempts = 0
  private var lastResponse = Status.Success
  private var loginFuture = CancellableFuture.successful[LoginResult](Left(ErrorResponse.Cancelled))

  def requestDelay =
    if (failedAttempts == 0) Duration.Zero
    else {
      val minDelay = if (lastResponse == Status.RateLimiting || lastResponse == Status.LoginRateLimiting) 5.seconds else Duration.Zero
      val nextRunTime = lastRequestTime + Throttling.delay(failedAttempts, minDelay).toMillis
      math.max(nextRunTime - System.currentTimeMillis(), 0).millis
    }

  override def login(credentials: Credentials) = throttled(loginNow(credentials))

  override def access(cookie: Cookie, token: Option[AccessToken]) = throttled(accessNow(cookie, token))

  def throttled(request: => CancellableFuture[LoginResult]): CancellableFuture[LoginResult] = dispatcher {
    loginFuture = loginFuture.recover {
      case e: CancelException => Left(ErrorResponse.Cancelled)
      case ex: Throwable =>
        tracking.exception(ex, "Unexpected error when trying to log in.")
        Left(ErrorResponse.internalError("Unexpected error when trying to log in: " + ex.getMessage))
    } flatMap { _ =>
      verbose(s"throttling, delay: $requestDelay")
      CancellableFuture.delay(requestDelay)
    } flatMap { _ =>
      verbose(s"starting request")
      lastRequestTime = System.currentTimeMillis()
      request.map {
        case Left(error) =>
          failedAttempts += 1
          lastResponse = error.code
          Left(error)
        case resp =>
          failedAttempts = 0
          lastResponse = Status.Success
          resp
      }
    }
    loginFuture
  }.flatten

  def loginNow(credentials: Credentials) = {
    debug(s"trying to login with credentials: $credentials")
    val label = Label()
    val params = JsonEncoder { o =>
      credentials.addToLoginJson(o)
      o.put("label", label.str)
    }
    val request = Request.Post(LoginUriStr, JsonContentEncoder(params), baseUri = Some(backend.baseUrl), timeout = RegistrationClientImpl.timeout)
    client(request).map(responseHandler(Some(label)))
  }

  def accessNow(cookie: Cookie, token: Option[AccessToken]) = {
    val headers = token.fold(Request.EmptyHeaders)(_.headers) ++ cookie.headers
    val request = Request.Post[Unit](AccessPath, data = EmptyRequestContent, baseUri = Some(backend.baseUrl), headers = headers, timeout = RegistrationClientImpl.timeout)
    client(request).map(responseHandler(None))
  }

  private def responseHandler(cookieLabel: Option[Label]): PartialFunction[Response, LoginResult] = {
    case Response(SuccessHttpStatus(), JsonObjectResponse(TokenResponse(token)), responseHeaders) =>
      debug(s"receivedAccessToken: '$token', headers: $responseHeaders")
      Right((token, getCookieFromHeaders(responseHeaders), cookieLabel))
    case r @ Response(_, ErrorResponse(code, msg, label), _) =>
      warn(s"failed login attempt: $r")
      Left(ErrorResponse(code, msg, label))
    case r @ Response(status, _, _) => Left(ErrorResponse(status.status, s"unexpected login response: $r", ""))
  }

  override def getSelfUserInfo(token: AccessToken) = {
    val request = Request.Get(UsersClient.SelfPath, baseUri = Some(backend.baseUrl), headers = token.headers)
    client(request).map {
      case Response(SuccessHttpStatus(), UsersClient.UserResponseExtractor(user), _) => Right(user)
      case resp =>
        info(s"Failed to get self user id")
        Left(ErrorResponse(resp.status.status, resp.status.msg, "unknown"))
    }.future
  }

  override def findSelfTeam(accessToken: AccessToken, start: Option[TeamId] = None) =
    getTeams(accessToken, start).flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(TeamBindingResponse(teams, hasMore)) =>
        teams.find(_._2).map(_._1) match {
          case Some(teamId) => Future.successful(Right(Some(teamId)))
          case None if hasMore => findSelfTeam(accessToken, teams.lastOption.map(_._1.id))
          case None => Future.successful(Right(None))
        }
    }

  override def getTeams(token: AccessToken, start: Option[TeamId]) = {
    val request = Request.Get(teamsPaginatedQuery(start), baseUri = Some(backend.baseUrl), headers = token.headers)
    client(request).map {
      case Response(SuccessHttpStatus(), TeamBindingResponse(teams, hasMore), _) => Right(TeamBindingResponse(teams, hasMore))
      case resp =>
        info(s"Failed to get self user id")
        Left(ErrorResponse(resp.status.status, resp.status.msg, "unknown"))
    }.future
  }

}

object LoginClient {
  type LoginResult = Either[ErrorResponse, (AccessToken, Option[Cookie], Option[Label])]

  val SetCookie = "Set-Cookie"
  val Cookie = "Cookie"
  val CookieHeader = ".*zuid=([^;]+).*".r
  val LoginPath = "/login"
  val AccessPath = "/access"
  val ActivateSendPath = "/activate/send"
  val LoginUriStr = Request.query(LoginPath, ("persist", true))

  val Throttling = new ExponentialBackoff(1000.millis, 10.seconds)

  def getCookieFromHeaders(headers: Response.Headers): Option[Cookie] = headers(SetCookie) flatMap {
    case header @ CookieHeader(cookie) =>
      verbose(s"parsed cookie from header: $header, cookie: $cookie")
      Some(AuthenticationManager.Cookie(cookie))
    case header =>
      warn(s"Unexpected content for Set-Cookie header: $header")
      None
  }

  object TokenResponse {
    def unapply(json: JSONObject): Option[AccessToken] = {
      implicit val js = json
      import com.waz.utils.JsonDecoder._
      Try(AccessToken('access_token, 'token_type, clock.instant() + bp.Duration.ofMillis(('expires_in: Long) * 1000))).toOption
    }
  }
}
