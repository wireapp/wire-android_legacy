/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.appentry.controllers

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.api.{EmailCredentials, PhoneCredentials}
import com.waz.model.{ConfirmationCode, EmailAddress, PhoneNumber}
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.appentry.EntryError
import com.waz.zclient.appentry.controllers.SignInController._
import com.waz.zclient.newreg.fragments.country.{Country, CountryController}
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, NameValidator, PasswordValidator}
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class SignInController(implicit inj: Injector, eventContext: EventContext, context: Context) extends Injectable with CountryController.Observer {

  private lazy val accountsService    = inject[AccountsService]
  private lazy val tracking           = inject[GlobalTrackingController]

  lazy val isAddingAccount = accountsService.zmsInstances.map(_.nonEmpty)

  val uiSignInState = Signal[SignInMethod](SignInMethod(Register, Phone))

  val email    = Signal("")
  val password = Signal("")
  val name     = Signal("")
  val phone    = Signal("")

  lazy val countryController = new CountryController(context)
  countryController.addObserver(this)
  lazy val phoneCountry = Signal[Country]()

  lazy val nameValidator = new NameValidator()
  lazy val emailValidator = EmailValidator.newInstance()
  lazy val passwordValidator = PasswordValidator.instance(context)
  lazy val legacyPasswordValidator = PasswordValidator.instanceLegacy(context)

  lazy val isValid: Signal[Boolean] = uiSignInState.flatMap {
    case SignInMethod(Login, Email) =>
      for {
        email <- email
        password <- password
      } yield emailValidator.validate(email) && legacyPasswordValidator.validate(password)
    case SignInMethod(Register, Email) =>
      for {
        name <- name
        email <- email
        password <- password
      } yield nameValidator.validate(name) && emailValidator.validate(email) && passwordValidator.validate(password)
    case SignInMethod(_, Phone) =>
      phone.map(_.nonEmpty)
    case _ => Signal.empty[Boolean]
  }

  def attemptSignIn(): Future[Either[EntryError, Unit]] = {
    implicit val ec = Threading.Ui
    for {
      method      <- uiSignInState.head
      credentials <- method.inputType match {
        case Email =>
          for{
            email    <- email.head
            password <- password.head
          } yield EmailCredentials(EmailAddress(email), password)
        case Phone =>
          for {
            phone    <- phone.head
            code     <- phoneCountry.head.map(_.getCountryCode)
          } yield PhoneCredentials(PhoneNumber(phone), ConfirmationCode(code))
      }
      resp <- (method.signType match {
        case Login    => accountsService.login(credentials)
        case Register =>
          for {
            n    <- name.head
            resp <- accountsService.register(credentials, n) //TODO - do we want to persist the credentials in case the user kills the app during verification?
          } yield resp
      }).map {
        case Left(error) => Left(EntryError(error.code, error.label, method))
        case Right(_) => Right(())
      }
      _ = tracking.onEnteredCredentials(resp, method)
    } yield resp
  }

  override def onCountryHasChanged(country: Country): Unit = phoneCountry ! country

  def clearCredentials() = {
    email ! ""
    password ! ""
    name ! ""
    phone ! ""
  }
}

object SignInController {

  sealed trait SignType
  object Login extends SignType
  object Register extends SignType

  sealed trait InputType
  object Email extends InputType
  object Phone extends InputType

  case class SignInMethod(signType: SignType, inputType: InputType)
}
