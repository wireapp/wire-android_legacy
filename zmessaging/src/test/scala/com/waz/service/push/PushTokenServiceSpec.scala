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
package com.waz.service.push

import java.io.IOException

import com.waz.ZLog.ImplicitTag._
import com.waz.api.NetworkMode
import com.waz.content.{AccountStorage, GlobalPreferences}
import com.waz.model._
import com.waz.service.AccountsService.{InBackground, LoggedOut}
import com.waz.service.NetworkModeService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.{TestBackoff, TestGlobalPreferences}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.GoogleApi

import scala.concurrent.Future

class PushTokenServiceSpec extends AndroidFreeSpec {

  import Threading.Implicits.Background

  feature("Global token service") {

    val google         = mock[GoogleApi]
    val networkService = mock[NetworkModeService]
    val prefs          = new TestGlobalPreferences()
    val currentToken   = prefs.preference(GlobalPreferences.PushToken)

    val googlePlayAvailable = Signal(false)
    val networkMode         = Signal(NetworkMode.WIFI)

    def initGlobalService() = {
      (google.isGooglePlayServicesAvailable _).expects().anyNumberOfTimes().returning(googlePlayAvailable)
      (networkService.networkMode _).expects.anyNumberOfTimes().returning(networkMode)

      new GlobalTokenServiceImpl(google, prefs, networkService)
    }

    scenario("Fetches token on init if GCM available") {
      googlePlayAvailable ! true
      val token = PushToken("token")

      (google.getPushToken _).expects().returning(token)
      val global = initGlobalService()

      result(global._currentToken.signal.filter(_.contains(token)).head)
    }

    scenario("Failing push token generation should continually retry on IOException, if there is a network connection") {

      googlePlayAvailable ! true

      /*
      The network starts offline - we want the global token service to try registering once - this is because I'm not entirely sure
      what happens inside the google play services. Maybe sometimes, a token can be available if there is no internet, no idea. Anyway,
      try once, and then if it fails with an IOException - it's most likely because there is no connection, so then wait for the device
      to come back online, then try again
      */
      networkMode ! NetworkMode.OFFLINE

      val newToken = PushToken("new_token")
      var calls = 0
      (google.getPushToken _).expects.twice().onCall { _ =>
        calls += 1
        calls match {
          case 1 => throw new IOException()
          case 2 => newToken
          case _ => fail("unexpected number of calls")
        }
      }

      PushTokenService.ResetBackoff = TestBackoff()

      val global = initGlobalService()

      global.setNewToken()

      awaitAllTasks
      calls shouldEqual 1
      networkMode ! NetworkMode._4G
      result(currentToken.signal.filter(_.contains(newToken)).head)

    }

    scenario("Multiple simultaneous calls to set token only generate one while still processing") {

      googlePlayAvailable ! true

      await(currentToken := Some(PushToken("oldToken")))

      val token1 = PushToken("token1")
      val token2 = PushToken("token2")
      (google.getPushToken _).expects.returning(token1)
      (google.getPushToken _).expects.returning(token2)

      val global = initGlobalService()

      //it's hard to only call `setNewToken` while currently setting one for the sake of the test, so just fire two calls
      //quickly, and hope the second one always slips in before the first one is set.
      val res = global.setNewToken()
      global.setNewToken()
      result(currentToken.signal.filter(_.contains(token1)).head)
      await(res)

      //Repeat to make sure the future is freed up
      global.setNewToken()
      global.setNewToken()
      result(currentToken.signal.filter(_.contains(token2)).head)
    }

    scenario("Reset global token") {
      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      currentToken := Some(oldToken)
      googlePlayAvailable ! true
      await(currentToken.signal.filter(_.contains(oldToken)).head)

      (google.getPushToken _).expects().once().returning(newToken)
      //This needs to be called
      (google.deleteAllPushTokens _).expects().once()

      val global = initGlobalService()

      global.resetGlobalToken()
      await(currentToken.signal.filter(_.contains(newToken)).head)
    }
  }

  feature("Account token service") {

    val global           = mock[GlobalTokenService]
    val accStorage       = mock[AccountStorage]
    val sync             = mock[SyncServiceHandle]
    val loggedInAccounts = Signal(Set.empty[AccountData])

    val currentToken = Signal(Option.empty[PushToken])

    def accountDataOpt(accountId: UserId, token: Option[PushToken]): AccountData = AccountData(accountId, pushToken = token)
    def accountData(accountId: UserId, token: PushToken): AccountData = accountDataOpt(accountId, Some(token))

    def accountSignal(id: UserId) = loggedInAccounts.map(_.find(_.id == id)).collect { case Some(acc) => acc }

    def initTokenService(accountIds: Seq[UserId] = Seq(account1Id), syncs: Seq[SyncServiceHandle] = Seq(sync)) = {

      (accStorage.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
        loggedInAccounts.map(_.find(_.id == id)).collect { case Some(acc) => acc }
      }

      (accStorage.update _).expects(*, *).anyNumberOfTimes().onCall { (id, f) =>
        Future.successful {
          val account = loggedInAccounts.currentValue("").flatMap(_.find(_.id == id))

          returning(account.fold(Option.empty[(AccountData, AccountData)])(p => Some((p, f(p))))) {
            case Some((_, updated)) => loggedInAccounts.mutate { accs =>
              val removed = accs - accs.find(_.id == updated.id).get
              val res = removed + updated
              res
            }
            case _ =>
          }
        }
      }

      (global.currentToken _).expects().anyNumberOfTimes().returning(currentToken)

      accountIds.zip(syncs).map { case (id, sync) =>
        new PushTokenService(id, global, accounts, accStorage, sync)
      }
    }

    scenario("Remove Push Token event should create new token and delete all previous tokens") {

      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      currentToken ! Some(oldToken)

      loggedInAccounts ! Set(accountData(account1Id, oldToken))

      (global.resetGlobalToken _).expects(Vector(oldToken)).once().onCall { _ : Vector[PushToken] =>
        Future(currentToken ! Some(newToken))
      }

      (sync.deletePushToken _).expects(oldToken).once().returning(Future.successful(SyncId()))

      (sync.registerPush _).expects(newToken).once().onCall { _: PushToken =>
        Future {
          service.onTokenRegistered(newToken)
          SyncId()
        }
      }

      lazy val Seq(service) = initTokenService()

      //wait for first token to be set
      result(accountSignal(account1Id).filter(_.pushToken.contains(oldToken)).head)
      //delete first token in response to BE event
      service.eventProcessingStage(RConvId(), Vector(PushTokenRemoveEvent(oldToken, "sender", Some("client"))))
      //new token should be set
      result(accountSignal(account1Id).filter(_.pushToken.contains(newToken)).head)
    }

    scenario("What the race?") {

      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      currentToken ! Some(oldToken)

      loggedInAccounts ! Set(accountData(account1Id, oldToken))

      (global.resetGlobalToken _).expects(Vector(oldToken)).once().onCall { _ : Vector[PushToken] =>
        Future(currentToken ! Some(newToken))
      }

      (sync.deletePushToken _).expects(oldToken).once().returning(Future.successful(SyncId()))

      (sync.registerPush _).expects(newToken).once().onCall { _: PushToken =>
        Future {
          service.onTokenRegistered(newToken)
          SyncId()
        }
      }

      lazy val Seq(service) = initTokenService()

      //wait for first token to be set
      result(accountSignal(account1Id).filter(_.pushToken.contains(oldToken)).head)
      //delete first token in response to BE event
      service.eventProcessingStage(RConvId(), Vector(PushTokenRemoveEvent(oldToken, "sender", Some("client"))))
      //new token should be set
      result(accountSignal(account1Id).filter(_.pushToken.contains(newToken)).head)
    }

    scenario("If current user does not have matching registeredPush token, remove the old token and register the new one with our BE") {

      val oldToken = PushToken("oldToken")
      val newToken = PushToken("token")

      currentToken ! Some(newToken)
      loggedInAccounts ! Set(accountData(account1Id, oldToken))
      result(accountSignal(account1Id).filter(_.pushToken.contains(oldToken)).head)

      (sync.deletePushToken _).expects(oldToken).once().returning(Future.successful(SyncId()))

      (sync.registerPush _).expects(newToken).once().onCall { _: PushToken =>
        println("registerPush")
        Future {
          service.onTokenRegistered(newToken)
          SyncId()
        }
      }

      lazy val Seq(service) = initTokenService()
      service

      result(accountSignal(account1Id).filter(_.pushToken.contains(newToken)).head)
    }

    scenario("After user is logged out, clearing their current push token should NOT trigger new registration") {
      val token = PushToken("token")

      loggedInAccounts ! Set(accountData(account1Id, token))
      currentToken ! Some(token)
      updateAccountState(account1Id, InBackground)

      (sync.registerPush _).expects(*).never()

      lazy val service = initTokenService()

      service
      result(accountSignal(account1Id).filter(_.pushToken.contains(token)).head)

      loggedInAccounts ! Set.empty //user is logged out
      updateAccountState(account1Id, LoggedOut)

      /**
        * There can be a couple of instances of zms (and therefore the push token service) available.
        * So lets make sure we only register the account assigned to our instance. Logging in then with another account
        * shouldn't change the state of this instance.
        */
      loggedInAccounts ! Set(AccountData())

      awaitAllTasks
    }

    scenario("Add second account and it should get the current global token") {

      val token = PushToken("oldToken")

      //Start off
      val account1 = accountDataOpt(account1Id, Some(token))
      val account2 = accountDataOpt(UserId(), None)

      loggedInAccounts ! Set(account1, account2)
      updateAccountState(account2.id, InBackground)
      currentToken ! Some(token)

      val sync2 = mock[SyncServiceHandle]

      lazy val Seq(service1, service2) = initTokenService(Seq(account1Id, account2.id), Seq(sync, sync2))

      (sync2.registerPush _).expects(token).once().onCall { _: PushToken =>
        Future {
          service2.onTokenRegistered(token)
          SyncId()
        } (Threading.Background)
      }

      //trigger lazy vals
      service1
      service2

      result(accountSignal(account1Id).filter(_.pushToken.contains(token)).head)
      result(accountSignal(account2.id).filter(_.pushToken.contains(token)).head)
    }
  }
}
