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
package com.waz.service

import com.waz.api.PhoneCredentials
import com.waz.api.impl.ErrorResponse
import com.waz.client.RegistrationClient
import com.waz.content.{AccountStorage, AccountsStorageOld, GlobalPreferences}
import com.waz.model._
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.LoginClient
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import org.scalatest.Inside

import scala.concurrent.Future

class AccountsServiceSpec extends AndroidFreeSpec with Inside {

  val storage      = mock[AccountStorage]
  val phoneNumbers = mock[PhoneNumberService]
  val regClient    = mock[RegistrationClient]
  val loginClient  = mock[LoginClient]

  val prefs = new TestGlobalPreferences()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    //prevent migration - can fail tests
    await(prefs.preference(GlobalPreferences.FirstTimeWithTeams) := false)
    await(prefs.preference(GlobalPreferences.DatabasesRenamed) := true)
  }

//  feature("Registration") {

//    val phoneNumber = PhoneNumber("+0918273465")
//    val confirmationCode = ConfirmationCode("123")
//
//    scenario("Register phone should attempt register request and save necessary data in AccountStorage") {
//      val service = getAccountService
//
//
//      val res = service.register(PhoneCredentials(phoneNumber, confirmationCode), "test")
//
//      result(res)
//
//
//    }
//  }

//    scenario("Request for a phone registration that exists in the db should update the account to pending and request a code from backend") {
//      val service = getAccountService
//      var account = AccountDataOld()
//
//      (storage.findByPhone _).expects(*).once().returning(Future.successful(Some(account)))
//      (regClient.requestPhoneConfirmationCode _).expects(*, KindOfAccess.REGISTRATION).once().returning(CancellableFuture.successful[ActivateResult](ActivateResult.Success))
//      (storage.updateOrCreate _).expects(*, *, *).once().onCall{ (_, updater, _) =>
//        account = updater(account)
//        Future.successful(account)
//      }
//
//      result(service.registerPhone(phoneNumber)).shouldBe(Right(()))
//
//      account.phone.shouldBe(None)
//      account.pendingPhone.shouldBe(Some(phoneNumber))
//      account.regWaiting.shouldBe(true)
//      account.code.shouldBe(None)
//      account.cookie.shouldBe(None)
//    }
//
//    scenario("Validating a code for registration should update the pending phone to normal and save the confirmation code") {
//      val service = getAccountService
//      var account = AccountDataOld(pendingPhone = Some(phoneNumber))
//
//      (storage.get _).expects(*).once().returning(Future.successful(Some(account)))
//      (regClient.verifyPhoneNumber _).expects(*, *, true).once().returning(CancellableFuture.successful(Right(())))
//      (storage.update _).expects(*, *).once().onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some(account, account))
//      }
//
//      result(service.activatePhoneOnRegister(account.id, confirmationCode)).shouldBe(Right(()))
//
//      account.phone.shouldBe(Some(phoneNumber))
//      account.pendingPhone.shouldBe(None)
//      account.regWaiting.shouldBe(true)
//      account.code.shouldBe(Some(confirmationCode))
//      account.cookie.shouldBe(None)
//    }
//
//    scenario("Registering a name to a phone account should finish the registration") {
//      val service = getAccountService
//      var account = AccountDataOld(phone = Some(phoneNumber), code = Some(confirmationCode))
//
//      (storage.get _).expects(*).once().returning(Future.successful(Some(account)))
//      (regClient.register _).expects(account, *, *).once().returning(CancellableFuture.successful(Right(UserInfo(UserId()), Some(Cookie("")))))
//      (storage.update _).expects(*, *).once().onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some(account, account))
//      }
//      (storage.get _).expects(account.id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//
//      result(service.registerNameOnPhone(account.id, "Whisker Pants")).shouldBe(Right(()))
//
//      account.phone.shouldBe(Some(phoneNumber))
//      account.pendingPhone.shouldBe(None)
//      account.regWaiting.shouldBe(false)
//      account.code.shouldBe(None)
//      account.cookie.shouldBe(Some(Cookie("")))
//    }
//
//  }
//
//  feature("Phone login") {
//
//    val phoneNumber = PhoneNumber("+0918273465")
//    val confirmationCode = ConfirmationCode("123")
//
//    scenario("Request a login with a new phone number should create an account and set the phone to pending"){
//      val service = getAccountService
//
//      (storage.findByPhone _).expects(*).once().returning(Future.successful(Option.empty[AccountDataOld]))
//      (regClient.requestPhoneConfirmationCode _).expects(*, KindOfAccess.LOGIN).once().returning(CancellableFuture.successful(ActivateResult.Success))
//      (storage.updateOrCreate _).expects(*, *, *).once().onCall{ (_, _, creator) =>
//
//        creator.phone.shouldBe(None)
//        creator.pendingPhone.shouldBe(Some(phoneNumber))
//        creator.regWaiting.shouldBe(false)
//        creator.code.shouldBe(None)
//        creator.cookie.shouldBe(None)
//
//        Future.successful(creator)
//      }
//
//      result(service.loginPhone(phoneNumber)).shouldBe(Right(()))
//    }
//
//    scenario("Request a login with an existing phone number in db should update the account and set the phone to pending"){
//      val service = getAccountService
//      var account = AccountDataOld().copy(phone = Some(phoneNumber))
//
//      (storage.findByPhone _).expects(*).once().returning(Future.successful(Some(account)))
//      (regClient.requestPhoneConfirmationCode _).expects(*, KindOfAccess.LOGIN).once().returning(CancellableFuture.successful(ActivateResult.Success))
//      (storage.updateOrCreate _).expects(*, *, *).once().onCall{ (_, updater, _) =>
//        account = updater(account)
//        Future.successful(account)
//      }
//
//      result(service.loginPhone(phoneNumber)).shouldBe(Right(()))
//
//      account.phone.shouldBe(Some(phoneNumber))
//      account.pendingPhone.shouldBe(Some(phoneNumber))
//      account.regWaiting.shouldBe(false)
//      account.code.shouldBe(None)
//      account.cookie.shouldBe(None)
//    }
//
//    scenario("Activate the phone on login should set the pending phone to normal and set the cookie and token"){
//      val service = getAccountService
//      var account = AccountDataOld().copy(pendingPhone = Some(phoneNumber))
//
//      val cookie = Cookie("123")
//      val token = AccessToken("1", "2", 3)
//
//      (storage.get _).expects(account.id).anyNumberOfTimes().returning(Future.successful(Some(account)))
//      (loginClient.login _).expects(*).once().returning(CancellableFuture.successful(Right(token, Some(cookie))))
//      (storage.update _).expects(*, *).anyNumberOfTimes().onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//
//      result(service.loginPhone(account.id, confirmationCode)).shouldBe(Right(()))
//
//      account.phone.shouldBe(Some(phoneNumber))
//      account.pendingPhone.shouldBe(None)
//      account.regWaiting.shouldBe(false)
//      account.code.shouldBe(None)
//      account.cookie.shouldBe(Some(cookie))
//      account.accessToken.shouldBe(Some(token))
//    }
//  }
//
//  feature("Email registration") {
//
//    val name = "Whisker Pants"
//    val email = EmailAddress("whisker.pants@wire.com")
//    val password = "12345678"
//
//    scenario("Attempting registration should send a request with the data and create the appropriate account data") {
//
//      val cookie = Cookie("cookie")
//      var account = AccountDataOld()
//
//      (storage.findByEmail _).expects(email).once().returning(Future.successful(None))
//      (regClient.register _).expects(*, name, *).once().returning(CancellableFuture.successful(Right(UserInfo(UserId()), Some(cookie))))
//      (storage.updateOrCreate _).expects(*, *, *).once.onCall{ (id, updater, creator) =>
//        updater(AccountDataOld(id)).shouldBe(creator)
//        account = creator
//        Future.successful(creator)
//      }
//
//      (storage.update _).expects(*, *).anyNumberOfTimes.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//      (storage.get _).expects(*).anyNumberOfTimes().onCall{ (id: AccountId) =>
//        if (id == account.id)
//        Future.successful(Some(account))
//        else
//        Future.successful(Some(AccountDataOld(id)))
//      }
//
//      result(getAccountService.registerEmail(email, password, name)).shouldBe(Right(()))
//
//      account.pendingEmail.shouldBe(Some(email))
//      account.email.shouldBe(None)
//      account.regWaiting.shouldBe(false)
//      account.cookie.shouldBe(Some(cookie))
//      account.password.isDefined.shouldBe(true)
//    }
//  }
//
//  feature("Email login") {
//
//    val email = EmailAddress("whisker.pants@wire.com")
//    val password = "12345678"
//
//    scenario("Login with a new email should create a the appropriate account data"){
//      val service = getAccountService
//
//      val cookie = Cookie("cookie")
//      val token = AccessToken("1", "2", 3)
//      var account = AccountDataOld()
//
//      (storage.findByEmail _).expects(email).once().returning(Future.successful(None))
//      (loginClient.login _).expects(*).returning(CancellableFuture.successful(Right((token, Some(cookie)))))
//      (storage.updateOrCreate _).expects(*, *, *).once.onCall{ (id, updater, creator) =>
//        updater(AccountDataOld(id)).shouldBe(creator)
//        account = creator
//        Future.successful(creator)
//      }
//
//      (storage.update _).expects(*, *).once.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//      (storage.get _).expects(*).anyNumberOfTimes().onCall{ (id: AccountId) =>
//        if (id == account.id)
//          Future.successful(Some(account))
//        else
//          Future.successful(Some(AccountDataOld(id)))
//      }
//
//      result(service.loginEmail(email, password)).shouldBe(Right(()))
//
//      account.email.shouldBe(Some(email))
//      account.pendingEmail.shouldBe(None)
//      account.regWaiting.shouldBe(false)
//      account.cookie.shouldBe(Some(cookie))
//      account.accessToken.shouldBe(Some(token))
//      account.password.isDefined.shouldBe(true)
//    }
//
//  }
//
//  feature("Team creation") {
//
//    scenario("create new account with pending team name") {
//      import com.waz.threading.Threading.Implicits.Background
//      val teamName = "team_name"
//
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(None))
//      (storage.insert _).expects(*).once().onCall { acc: AccountDataOld =>
//        if (!acc.pendingTeamName.contains(teamName)) fail("Team name did not match") else Future.successful(acc)
//      }
//
//      val service = getAccountService
//
//      val idsMatch = for {
//        id <- service.createTeamAccount(teamName)
//        pref <- prefs.preference(GlobalPreferences.CurrentAccountPrefOld).apply()
//      } yield pref.contains(id)
//
//      result(idsMatch) shouldEqual true
//    }
//
//    scenario("Request confirmation code on current account should set pending email if successful") {
//      val teamName = "team_name"
//
//      var account = AccountDataOld(id = AccountId(), pendingTeamName = Some(teamName))
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(Some(account)))
//
//      val service = getAccountService
//      service.createTeamAccount(teamName)
//
//      val email = EmailAddress("test@test.com")
//
//      (storage.update _).expects(account.id, *).once.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//
//      (regClient.requestEmailCode _).expects(email).returning(CancellableFuture.successful(ActivateResult.Success))
//      result(service.requestActivationCode(email)) shouldEqual Right(())
//
//      account.pendingEmail should contain(email)
//    }
//
//    scenario("Verify confirmation code sets code if result successful") {
//      val teamName = "team_name"
//      val code = ConfirmationCode("123456")
//      val email = EmailAddress("test@test.com")
//
//      var account = AccountDataOld(id = AccountId(), pendingTeamName = Some(teamName), pendingEmail = Some(email))
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(Some(account)))
//      val service = getAccountService
//      service.createTeamAccount(teamName)
//
//      (storage.get _).expects(account.id).returning(Future.successful(Some(account)))
//      (storage.update _).expects(account.id, *).once.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//
//      (regClient.verifyEmail _).expects(email, code).returning(CancellableFuture.successful(Right(())))
//      result(service.verify(code)) shouldEqual Right(())
//
//      account.code should contain(code)
//    }
//
//    scenario("Verify confirmation code does not set code if unsuccessful") {
//      val teamName = "team_name"
//      val code = ConfirmationCode("123456")
//      val email = EmailAddress("test@test.com")
//
//      var account = AccountDataOld(id = AccountId(), pendingTeamName = Some(teamName), pendingEmail = Some(email))
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(Some(account)))
//      val service = getAccountService
//      await(service.createTeamAccount(teamName))
//
//      (storage.get _).expects(account.id).returning(Future.successful(Some(account)))
//      (storage.update _).expects(account.id, *).never()
//
//      val error = ErrorResponse(1, "some error", "")
//      (regClient.verifyEmail _).expects(email, code).returning(CancellableFuture.successful(Left(error)))
//      result(service.verify(code)) shouldEqual Left(error)
//
//      account.code shouldBe empty
//    }
//
//    scenario("Register account") {
//      val teamName = "team_name"
//      val email = EmailAddress("test@test.com")
//      val pw    = "password"
//      val name  = "account_name"
//
//      var account = AccountDataOld(
//        id              = AccountId(),
//        pendingTeamName = Some(teamName),
//        name            = Some(name),
//        pendingEmail    = Some(email),
//        password        = Some(pw),
//        code            = Some(ConfirmationCode("123456"))
//      )
//
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(Some(account)))
//
//      val service = getAccountService
//      service.createTeamAccount(teamName)
//
//      //see https://staging-nginz-https.zinfra.io/swagger-ui/#!/users/register for expected return details
//      val expectedUserInfo = UserInfo(
//        id   = UserId("user_id"),
//        name = account.name
//      )
//
//      val cookie = Cookie("cookie")
//
//      (storage.get _).expects(account.id).returning(Future.successful(Some(account)))
//
//      (storage.update _).expects(*, *).once.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//
//      (regClient.registerTeamAccount _).expects(account).returning(CancellableFuture.successful(Right((expectedUserInfo, Some(cookie)))))
//      result(service.register()) shouldEqual Right(())
//
//      inside (account) { case a =>
//        a.teamId          shouldEqual Left(()) //team should remain undefined - we will fetch this before creating ZMS
//        a.pendingTeamName shouldEqual Some(teamName) // teamName should still be pending until we finish all team creation steps
//        a.name            shouldEqual Some(name)
//        a.email           shouldEqual Some(email)
//        a.pendingEmail    shouldEqual None
//        a.password        shouldEqual Some(pw)
//        a.cookie          shouldEqual Some(cookie)
//        a.userId          shouldEqual Some(expectedUserInfo.id)
//      }
//    }
//
//    scenario("Registering current account should remove confirmation code if invalid-code is returned") {
//      val teamName = "team_name"
//      val email = EmailAddress("test@test.com")
//      val pw    = "password"
//      val name  = "account_name"
//
//      var account = AccountDataOld(
//        id              = AccountId(),
//        pendingTeamName = Some(teamName),
//        name            = Some(name),
//        pendingEmail    = Some(email),
//        password        = Some(pw),
//        code            = Some(ConfirmationCode("123456"))
//      )
//
//      (storage.findByPendingTeamName _).expects(teamName).once().returning(Future.successful(Some(account)))
//
//      val service = getAccountService
//      service.createTeamAccount(teamName)
//
//      (storage.get _).expects(account.id).returning(Future.successful(Some(account)))
//
//      (storage.update _).expects(*, *).once.onCall{ (_, updater) =>
//        account = updater(account)
//        Future.successful(Some((account, account)))
//      }
//
//      val error = ErrorResponse(Response.Status.NotFound, "", "invalid-code")
//      (regClient.registerTeamAccount _).expects(account).returning(CancellableFuture.successful(Left(error)))
//      result(service.register()) shouldEqual Left(error)
//
//      inside (account) { case a =>
//        a.teamId          shouldEqual Left(()) //team should remain undefined - we will fetch this before creating ZMS
//        a.pendingTeamName shouldEqual Some(teamName)
//        a.name            shouldEqual Some(name)
//        a.email           shouldEqual None
//        a.pendingEmail    shouldEqual Some(email)
//        a.password        shouldEqual None
//        a.cookie          shouldEqual None
//        a.userId          shouldEqual None
//      }
//    }
//  }
//
//  scenario("Getting the active account should access the storage") {
//    val accountId = AccountId()
//
//    prefs.setValue(GlobalPreferences.CurrentAccountPrefOld, Some(accountId))
//
//    val service = getAccountService
//
//    var account = AccountDataOld(
//      id              = accountId,
//      pendingTeamName = Some("team_name"),
//      name            = Some("account_name"),
//      pendingEmail    = Some(EmailAddress("test@test.com")),
//      password        = Some("password"),
//      code            = Some(ConfirmationCode("123456"))
//    )
//
//    (storage.get _).expects(accountId).atLeastOnce().returning(Future.successful(Some(account)))
//
//    result(service.getActiveAccount) shouldEqual Some(account)
//  }
//
  def getAccountService: AccountsServiceImpl = {

    val globalModule = mock[GlobalModule]

    (globalModule.accountsStorage _).expects().anyNumberOfTimes.returning(storage)
    (globalModule.trackingService _).expects().anyNumberOfTimes.returning(tracking)
    (globalModule.phoneNumbers _).expects().anyNumberOfTimes.returning(phoneNumbers)
    (globalModule.regClient _).expects().anyNumberOfTimes.returning(regClient)
    (globalModule.loginClient _).expects().anyNumberOfTimes.returning(loginClient)
    (globalModule.prefs _).expects().anyNumberOfTimes.returning(prefs)
    (globalModule.factory _).expects().anyNumberOfTimes.returning(new ZMessagingFactory(globalModule))
    (globalModule.context _).expects().anyNumberOfTimes().returning(null)
    (globalModule.lifecycle _).expects().anyNumberOfTimes().returning(new UiLifeCycleImpl)

    (phoneNumbers.normalize _).expects(*).anyNumberOfTimes().onCall { p: PhoneNumber => Future.successful(Some(p)) }

    (storage.list _).expects().anyNumberOfTimes().returning(Future.successful(Seq.empty[AccountData]))
    (storage.updateAll2 _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Seq()))
    (storage.onChanged _).expects().anyNumberOfTimes().returning(new EventStream[Seq[AccountData]]())
    (storage.onDeleted _).expects().anyNumberOfTimes().returning(new EventStream[Seq[UserId]]())
    (storage.signal _).expects(*).anyNumberOfTimes().returning(Signal.empty[AccountData])
    (storage.optSignal _).expects(*).anyNumberOfTimes().returning(Signal.empty[Option[AccountData]])

    new AccountsServiceImpl(globalModule)
  }
}
