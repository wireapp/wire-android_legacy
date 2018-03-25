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
import com.waz.api.ErrorResponse
import com.waz.model.EmailAddress
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, PasswordValidator}
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.Future

class AddEmailController(implicit inj: Injector, eventContext: EventContext, context: Context) extends Injectable {

  lazy val zms = inject[Signal[ZMessaging]]

  val email = Signal("")
  val password = Signal("")

  lazy val emailValidator = EmailValidator.newInstance()
  lazy val passwordValidator = PasswordValidator.instance(context)

  lazy val isValid: Signal[Boolean] = for {
    email <- email
    password <- password
  } yield emailValidator.validate(email) && passwordValidator.validate(password)

  //TODO
//  (for {
//    Some(accountManager) <- ZMessaging.currentAccounts.activeAccountManager
//    true <- isValid
//    password <- password
//    Some(_) <- ZMessaging.currentAccounts.activeAccount.map(_.flatMap(_.email))
//    None <- ZMessaging.currentAccounts.activeAccount.map(_.flatMap(_.pendingEmail))
//  } yield (accountManager, password)){ case (am, p) =>
//    am.updatePassword(p, None)
//  }

  def addEmailAndPassword(): Future[Either[ErrorResponse, Unit]] = {
    import com.waz.threading.Threading.Implicits.Background
    throw new NotImplementedError("")
    //TODO
//    for {
//      email <- email.head
//      Some(accountManager) <- ZMessaging.currentAccounts.activeAccountManager.head
//      emailRes <- accountManager.updateEmail(EmailAddress(email)).future
//      res <- emailRes match {
//        case Right(_) =>
//          accountManager.accounts.updateCurrentAccount(_.copy(pendingEmail = Some(EmailAddress(email))))
//            .map(_ => Right(()))
//        case Left(err) => Future.successful(Left(err))
//      }
//    } yield res
  }
}
