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

import java.util.UUID

import com.waz.log.LogSE._
import com.waz.api.Credentials
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Label
import com.waz.model.{TeamId, UserInfo}
import com.waz.model2.transport.Team
import com.waz.model2.transport.responses.{DomainVerificationResponse, FetchSsoResponse, TeamsResponse}
import com.waz.service.ZMessaging.clock
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.sync.client.LoginClient.LoginResult
import com.waz.sync.client.TeamsClient.{TeamsPageSize, TeamsPath}
import com.wire.signals.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.{ExponentialBackoff, JsonEncoder, _}
import com.waz.znet2.http
import com.waz.znet2.http.HttpClient.dsl._
import com.waz.znet2.http.HttpClient.AutoDerivationOld._
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json.JSONObject
import org.threeten.bp

import scala.concurrent.Future
import scala.concurrent.duration._

trait LoginClient {
  def access(cookie: Cookie, token: Option[AccessToken]): ErrorOr[LoginResult]
  def login(credentials: Credentials): ErrorOr[LoginResult]
  def getSelfUserInfo(token: AccessToken): ErrorOr[UserInfo]

  def findSelfTeam(accessToken: AccessToken, start: Option[TeamId] = None): ErrorOr[Option[Team]]
  def getTeams(accessToken: AccessToken, start: Option[TeamId]): ErrorOr[TeamsResponse]
  def verifySSOToken(token: UUID): ErrorOr[Boolean]
  def verifyDomain(domain: String): ErrorOr[DomainVerificationResponse]
  def fetchSSO(): ErrorOr[FetchSsoResponse]
}

class LoginClientImpl()
                     (implicit
                      urlCreator: UrlCreator,
                      client: HttpClient) extends LoginClient with DerivedLogTag {

  import LoginClient._

  private implicit val dispatcher = SerialDispatchQueue(name = "LoginClient")

  private var lastRequestTime = 0L
  private var failedAttempts = 0
  private var lastResponseCode = http.ResponseCode.Success
  private var loginFuture: ErrorOr[LoginResult] = Future.successful(Left(ErrorResponse.Cancelled))

  def requestDelay =
    if (failedAttempts == 0) Duration.Zero
    else {
      val minDelay = if (lastResponseCode == ResponseCode.RateLimiting || lastResponseCode == ResponseCode.LoginRateLimiting) 5.seconds else Duration.Zero
      val nextRunTime = lastRequestTime + Throttling.delay(failedAttempts, minDelay).toMillis
      math.max(nextRunTime - System.currentTimeMillis(), 0).millis
    }

  override def login(credentials: Credentials): ErrorOr[LoginResult] = throttled(loginNow(credentials))

  override def access(cookie: Cookie, token: Option[AccessToken]) = throttled(accessNow(cookie, token))

  def throttled(request: => ErrorOr[LoginResult]): ErrorOr[LoginResult] = dispatcher {
    loginFuture = loginFuture.recover {
      case ex: Throwable =>
        Left(ErrorResponse.internalError("Unexpected error when trying to log in: " + ex.getMessage))
    } flatMap { _ =>
      verbose(l"throttling, delay: $requestDelay")
      CancellableFuture.delay(requestDelay).future
    } flatMap { _ =>
      verbose(l"starting request")
      lastRequestTime = System.currentTimeMillis()
      request.map {
        case Left(error) =>
          failedAttempts += 1
          lastResponseCode = error.code
          Left(error)
        case resp =>
          failedAttempts = 0
          lastResponseCode = ResponseCode.Success
          resp
      }
    }
    loginFuture
  }.future.flatten

  private implicit val FIXED_AccessTokenDecoder: JsonDecoder[AccessToken] = new JsonDecoder[AccessToken] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AccessToken =
      AccessToken(
        'access_token,
        'token_type,
        clock.instant() + bp.Duration.ofMillis(('expires_in: Long) * 1000)
      )
  }

  def loginNow(credentials: Credentials): ErrorOr[LoginResult] = {
    debug(l"trying to login with credentials: $credentials")
    val label = Label()
    val params = JsonEncoder { o =>
      credentials.addToLoginJson(o)
      o.put("label", label.str)
    }
    Request
      .Post(relativePath = LoginPath, queryParameters = queryParameters("persist" -> true), body = params)
      .withResultType[http.Response[AccessToken]]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map { _.right.map(resp => LoginResult(resp.body, resp.headers, Some(label))) }
      .future
  }

  def accessNow(cookie: Cookie, token: Option[AccessToken]): ErrorOr[LoginResult] = {
    Request.Post(
        relativePath = AccessPath,
        headers = Headers(token.map(_.headers).getOrElse(Map.empty) ++ cookie.headers),
        body = ""
      )
      .withResultType[Response[AccessToken]]
      .withErrorType[ErrorResponse].executeSafe
      .map { _.right.map(resp => LoginResult(resp.body, resp.headers, None)) }
      .future
  }

  override def getSelfUserInfo(token: AccessToken): ErrorOr[UserInfo] = {
    Request.Get(relativePath = UsersClient.SelfPath, headers = http.Headers(token.headers))
      .withResultType[UserInfo]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def findSelfTeam(accessToken: AccessToken, start: Option[TeamId] = None): ErrorOr[Option[Team]] =
    getTeams(accessToken, start).flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(TeamsResponse(hasMore, teams)) =>
        teams.find(_.binding) match {
          case Some(team) => Future.successful(Right(Some(team)))
          case None if hasMore => findSelfTeam(accessToken, teams.lastOption.map(_.id))
          case None => Future.successful(Right(None))
        }
    }

  override def getTeams(token: AccessToken, start: Option[TeamId]): ErrorOr[TeamsResponse] = {
    Request
      .Get(
        relativePath = TeamsPath,
        headers = http.Headers(token.headers),
        queryParameters = queryParameters("size" -> TeamsPageSize, "start" -> start)
      )
      .withResultType[TeamsResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def verifySSOToken(token: UUID): ErrorOr[Boolean] = {
    Request.Head(relativePath = InitiateSSOLoginPath(token.toString))
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[Response[Unit]]
      .withErrorType[ErrorResponse]
      .executeSafe(r => ResponseCode.SuccessCodes.contains(r.code))
      .future
  }

  override def verifyDomain(domain: String): ErrorOr[DomainVerificationResponse] =
    Request.Get(relativePath = verifyDomainPath(domain))
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[DomainVerificationResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future


  override def fetchSSO(): ErrorOr[FetchSsoResponse]  =
    Request.Get(relativePath = fetchSsoPath())
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[FetchSsoResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
}

object LoginClient extends DerivedLogTag {

  case class LoginResult(accessToken: AccessToken, cookie: Option[Cookie], label: Option[Label])

  object LoginResult {

    def apply(accessToken: AccessToken, headers: http.Headers, label: Option[Label]): LoginResult =
      new LoginResult(accessToken, getCookieFromHeaders(headers), label)

  }

  val SetCookie = "Set-Cookie"
  val Cookie = "Cookie"
  val CookieHeader = ".*zuid=([^;]+).*".r
  val LoginPath = "/login"
  val AccessPath = "/access"
  val ActivateSendPath = "/activate/send"

  val Throttling = new ExponentialBackoff(1000.millis, 10.seconds)

  def InitiateSSOLoginPath(code: String) = s"/sso/initiate-login/$code"

  def verifyDomainPath(domain: String) = s"/custom-backend/by-domain/$domain"

  def fetchSsoPath() = s"/sso/settings"

  def getCookieFromHeaders(headers: Headers): Option[Cookie] = headers.get(SetCookie) flatMap {
    case CookieHeader(cookie) =>
      Some(returning(AuthenticationManager.Cookie(cookie)) { cookie =>
        verbose(l"parsed cookie from header, cookie: $cookie")
      })

    case _ =>
      warn(l"Unexpected content for Set-Cookie header")
      None
  }

}
