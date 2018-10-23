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

import com.waz.api.EmailCredentials
import com.waz.api.impl.ErrorResponse
import com.waz.content.AccountStorage2
import com.waz.model.AccountData.{Label, Password}
import com.waz.model._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.sync.client.LoginClient.LoginResult
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.{RichInstant, Serialized, returning}
import com.waz.znet2.http.ResponseCode

import scala.concurrent.Future
import scala.concurrent.duration._

//class AuthenticationManagerSpec extends AndroidFreeSpec {
//
//  val loginClient = mock[client.LoginClient]
//  val accStorage  = mock[AccountStorage2]
//
//  feature("Successful logins") {
//    scenario("Return authentication token if valid") {
//
//      val account = AccountData(
//        account1Id,
//        cookie      = Cookie("cookie"),
//        accessToken = Some(AccessToken("token", "token", clock.instant() + client.AuthenticationManager.ExpireThreshold + 1.second)))
//
//      (accStorage.load _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//      val manager = getManager
//
//      result(manager.currentToken()) shouldEqual Right(AccessToken("token", "token", clock.instant() + client.AuthenticationManager.ExpireThreshold + 1.second))
//    }
//
//    scenario("Request new token if old token is invalid") {
//
//      val oldToken = AccessToken("token", "token", clock.instant())
//      val newToken = AccessToken("newToken", "token", clock.instant() + 15.minutes)
//
//      val account = AccountData(
//        account1Id,
//        cookie      = Cookie("cookie"),
//        accessToken = Some(oldToken))
//
//      val newAccount = account.copy(accessToken = Some(newToken))
//
//      (accStorage.load _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//
//      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
//        updater(account) shouldEqual newAccount
//        Future.successful(Some((account, newAccount)))
//      }
//
//      (loginClient.access _).expects(account.cookie, Some(oldToken)).returning(Future.successful(Right(LoginResult(newToken, None, None))))
//
//      val manager = getManager
//
//      result(manager.currentToken()) shouldEqual Right(newToken)
//    }
//
//    scenario("Cookie in access response should be saved") {
//
//      val oldToken = AccessToken("token", "token", clock.instant())
//      val newToken = AccessToken("newToken", "token", clock.instant() + 15.minutes)
//
//      val oldCookie = Cookie("cookie")
//      val newCookie = Cookie("newCookie")
//
//      val account = AccountData(
//        account1Id,
//        cookie      = oldCookie,
//        accessToken = Some(oldToken))
//
//      val newAccount = account.copy(cookie = newCookie, accessToken = Some(newToken))
//
//      (accStorage.get _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//      (accStorage.update _).expects(*, *).onCall { (_, updater) =>
//        updater(account) shouldEqual newAccount
//        Future.successful(Some((account, newAccount)))
//      }
//
//      (loginClient.access _).expects(account.cookie, account.accessToken).returning(Future.successful(Right(LoginResult(newToken, Some(newCookie), Some(Label(""))))))
//      val manager = getManager
//
//      result(manager.currentToken()) shouldEqual Right(newToken)
//    }
//
//    scenario("Multiple calls to access should only trigger at most one request") {
//      val oldToken = AccessToken("token", "token", clock.instant())
//      val newToken = AccessToken("newToken", "token", clock.instant() + 15.minutes)
//
//      @volatile var account = AccountData(
//        account1Id,
//        cookie      = Cookie("cookie"),
//        accessToken = Some(oldToken))
//
//
//      (accStorage.load _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
//        Threading.Background(Some(account)).future
//      }
//
//      (accStorage.save _).expects(*, *).onCall { (id, updater) =>
//        Serialized.future("update-account") {
//          val old = account
//          account = updater(account)
//          Future.successful(Some((old, account)))
//        }
//      }
//
//      (loginClient.access _).expects(account.cookie, Some(oldToken)).once().returning(Future.successful(Right(LoginResult(newToken, None, None))))
//
//      val manager = getManager
//
//      import Threading.Implicits.Background
//      val futures = Future.sequence((1 to 10).map(_ => manager.currentToken()))
//
//      result(futures).foreach(_ shouldEqual Right(newToken))
//    }
//  }
//
//  feature("Insufficient login information") {
//    scenario("Log account out when cookie and access token are invalid") {
//
//      val account = AccountData(
//        account1Id,
//        cookie      = Cookie("cookie"),
//        accessToken = Some(AccessToken("token", "token", clock.instant())))
//
//      (accStorage.load _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//
//      (accStorage.delete _).expects(account1Id).returning(Future.successful({}))
//
//      (loginClient.access _).expects(account.cookie, account.accessToken).returning(Future.successful(Left(ErrorResponse(ResponseCode.Forbidden, "", ""))))
//
//      val manager = getManager
//
//      result(manager.currentToken()) shouldEqual Left(ErrorResponse(ResponseCode.Forbidden, "", ""))
//    }
//
//    scenario("Account logout should cancel authentication attempts") {
//
//      (accStorage.load _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(None))
//
//      val manager = getManager
//      result(manager.currentToken()) shouldEqual Left(ErrorResponse.Unauthorized)
//    }
//  }
//
//  feature ("on reset password") {
//    scenario("call onResetPassword with password still in memory concurrently with currentToken request") {
//
//      val oldToken = AccessToken("token", "token", clock.instant())
//      val newToken = AccessToken("newToken", "token", clock.instant() + 15.minutes)
//
//      val oldCookie = Cookie("cookie")
//      val newCookie = Cookie("newCookie")
//
//      val emailCredentials = EmailCredentials(EmailAddress("test@123.com"), Password("password"))
//
//      @volatile var account = AccountData(
//        account1Id,
//        cookie      = oldCookie,
//        accessToken = Some(oldToken))
//
//      (accStorage.load _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
//        println("hmm")
//        Threading.Background(Some(account)).future
//      }
//
//      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
//        Serialized.future("update-account") {
//          val old = account
//          account = updater(account)
//          Future.successful(Some((old, account)))
//        }
//      }
//
//      (loginClient.access _).expects(account.cookie, None).once().returning(Future.successful(Left(ErrorResponse(ResponseCode.Forbidden, "", ""))))
//      (loginClient.login _).expects(emailCredentials).once().returning(Future.successful(Right(LoginResult(newToken, Some(newCookie), Some(Label("label"))))))
//      val manager = getManager
//
//      manager.onPasswordReset(Some(emailCredentials))
//      result(manager.currentToken()) shouldEqual Right(newToken)
//    }
//  }
//
//  feature("Failures") {
//    scenario("Retry if login not successful for unknown reasons") {
//
//      val oldToken = AccessToken("token", "token", clock.instant())
//      val newToken = AccessToken("newToken", "token", clock.instant() + 15.minutes)
//
//      val account = AccountData(
//        account1Id,
//        cookie      = Cookie("cookie"),
//        accessToken = Some(oldToken))
//
//      val newAccount = account.copy(accessToken = Some(newToken))
//
//      (accStorage.load _).expects(account1Id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//
//      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
//        updater(account) shouldEqual newAccount
//        Future.successful(Some((account, newAccount)))
//      }
//
//      var attempts = 0
//      (loginClient.access _).expects(account.cookie, Some(oldToken)).anyNumberOfTimes().onCall { (cookie, token) =>
//        returning(CancellableFuture.successful(attempts match {
//          case 0|1|2 => Left(ErrorResponse(500, "Some server error", "Some server error"))
//          case 3     => Right(LoginResult(newToken, None, None))
//          case _     => fail("Unexpected number of access attempts")
//        }))(_ => attempts += 1)
//      }
//
//      val manager = getManager
//
//      result(manager.currentToken()) shouldEqual Right(newToken)
//    }
//  }
//
//  def getManager = new client.AuthenticationManager(account1Id, accStorage, loginClient, tracking)
//}
