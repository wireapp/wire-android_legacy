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

import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.api.ImageAsset
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.model._
import com.waz.model.otr.{ClientId, UserClients}
import com.waz.service.ZMessaging
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.appentry.EntryError
import com.waz.zclient.appentry.controllers.AppEntryController._
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment
import com.waz.zclient.newreg.fragments.SignUpPhotoFragment.RegistrationType
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.{Injectable, Injector}
import com.waz.znet.ZNetClient.ErrorOr

import scala.concurrent.Future

class AppEntryController(implicit inj: Injector, eventContext: EventContext) extends Injectable {

  implicit val ec = Threading.Background

  lazy val prefs      = inject[GlobalPreferences]
  lazy val optZms     = inject[Signal[Option[ZMessaging]]]
  lazy val tracking   = inject[TrackingService]
  lazy val uiTracking = inject[GlobalTrackingController] //TODO slowly move away from referencing this class

  val currentAccount = ZMessaging.currentAccounts.activeAccount
  val currentUser    = optZms.flatMap{ _.fold(Signal.const(Option.empty[UserData]))(z => z.usersStorage.optSignal(z.selfUserId)) }
  val firstStage     = Signal[FirstStage](FirstScreen)

  val userClientsCount = for {
    Some(manager) <- ZMessaging.currentAccounts.activeAccountManager
    Some(user)    <- currentUser
    selfClientId  <- manager.clientId
    clients       <- Signal.future(manager.storage.otrClientsStorage.get(user.id))
  } yield clients.fold(0)(_.clients.values.count(client => !selfClientId.contains(client.id)))

  val userHasOtherClients = for {
    manager      <- ZMessaging.currentAccounts.activeAccountManager
    user         <- currentUser.map(_.map(_.id))
    selfClientId <- manager.fold(Signal.const(Option.empty[ClientId]))(_.clientId)
    clients      <- (manager, user) match {
      case (Some(m), Some(u)) => m.storage.otrClientsStorage.signal(u).map(Some(_))
      case _ => Signal.const(Option.empty[UserClients])
    }
    clientCount = clients.fold(0)(_.clients.values.count(client => !selfClientId.contains(client.id)))
    _ = ZLog.verbose(s"userClientsCount $manager $user $clientCount")
  } yield clientCount >= 1


  //TODO move prefs down to SE
  //TODO should these be typed? (e.g. EmailAddress)
  val pendingTeamName = prefs(PrefKey[Option[String]]("pending_team"))
  val pendingEmail    = prefs(PrefKey[Option[String]]("pending_email"))
  val pendingPhone    = prefs(PrefKey[Option[String]]("pending_phone"))

  val pendingPassword = Signal(Option.empty[String]) //TODO make Password type with obfuscated toString
  val pendingCode     = Signal(Option.empty[ConfirmationCode])

  //Vars to persist text in edit boxes
  var teamName = ""
  var teamEmail = ""
  var code = ""
  var teamUserName = ""
  var teamUserUsername = ""
  var password = ""

  def clearCredentials(): Unit = {
    teamName = ""
    teamEmail = ""
    code = ""
    teamUserName = ""
    teamUserUsername = ""
    password = ""
  }

//  val entryStage = for {
//    account         <- currentAccount
//    user            <- currentUser.orElse(Signal.const(None))
//    firstPageState  <- firstStage
//    hasOtherClients <- userHasOtherClients
//    state           <- Signal.const(stateForAccountAndUser(account, user, firstPageState, hasOtherClients)).collect { case s if s != Waiting => s }
//  } yield state

//  entryStage.onUi { stage =>
//    ZLog.verbose(s"Current stage: $stage")
//    stage match {
//      case NoAccountState(FirstScreen) => tracking.track(OpenedStartScreen())
//      case NoAccountState(RegisterTeamScreen) => tracking.track(OpenedTeamRegistration())
//      case _ =>
//    }
//  }

  ZMessaging.currentAccounts.loggedInAccounts.map(_.isEmpty) {
    case true =>
      firstStage ! FirstScreen
    case false =>
  }


  def setTeamName(name: String): Future[Unit] =
    pendingTeamName := Some(name)

  //TODO remove methods?
  def gotToFirstPage(): Unit = firstStage ! FirstScreen

  def createTeam(): Unit = firstStage ! RegisterTeamScreen

  def cancelCreateTeam(): Unit = firstStage ! FirstScreen

  def goToLoginScreen(): Unit = firstStage ! LoginScreen


  def createTeamBack(): Unit = {
//    ZMessaging.currentAccounts.activeAccount.currentValue.foreach {
//      case Some(accountData) if accountData.pendingTeamName.isDefined && accountData.name.isDefined =>
//        password = ""
//        ZMessaging.currentAccounts.updateCurrentAccount(_.copy(name = None))
//      case Some(accountData) if accountData.pendingTeamName.isDefined && accountData.code.isDefined =>
//        teamUserName = ""
//        code = ""
//        ZMessaging.currentAccounts.updateCurrentAccount(_.copy(code = None, pendingEmail = None))
//      case Some(accountData) if accountData.pendingTeamName.isDefined && accountData.pendingEmail.isDefined =>
//        code = ""
//        ZMessaging.currentAccounts.updateCurrentAccount(_.copy(pendingEmail = None))
//      case Some(accountData) if accountData.pendingTeamName.isDefined =>
//        teamEmail = ""
//        ZMessaging.currentAccounts.logout(false)
//      case _ =>
//        teamName = ""
//        Future.successful(firstStage ! FirstScreen)
//    }
  }

  def verifyPhone(code: String): Future[Either[EntryError, Unit]] = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.activeAccount.head.flatMap {
//      case Some(accountData) if accountData.regWaiting =>
//        val method = SignInMethod(Register, Phone)
//        ZMessaging.currentAccounts.activatePhoneOnRegister(accountData.id, ConfirmationCode(code)).map {
//          case Left(error) =>
//            val entryError = EntryError(error.code, error.label, method)
//            uiTracking.onEnterCode(Left(entryError), method)
//            Left(entryError)
//          case _ =>
//            uiTracking.onEnterCode(Right(()), method)
//            Right(())
//        }
//      case Some(accountData) =>
//        val method = SignInMethod(Login, Phone)
//        ZMessaging.currentAccounts.loginPhone(accountData.id, ConfirmationCode(code)).flatMap {
//          case Left(error) =>
//            val entryError = EntryError(error.code, error.label, method)
//            uiTracking.onEnterCode(Left(entryError), method)
//            Future.successful(Left(entryError))
//          case _ =>
//            uiTracking.onEnterCode(Right(()), method)
//            ZMessaging.currentAccounts.switchAccount(accountData.id).map(_ => Right(()))
//        }
//      case _ => Future.successful(Left(GenericRegisterPhoneError))
//    }
  }

  def registerName(name: String): Future[Either[EntryError, Unit]] = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.activeAccount.head.flatMap {
//      case Some(accountData) if accountData.phone.isDefined && accountData.regWaiting && accountData.code.isDefined =>
//        ZMessaging.currentAccounts.registerNameOnPhone(accountData.id, name).flatMap {
//          case Left(error) =>
//            val entryError = EntryError(error.code, error.label, SignInMethod(Register, Phone))
//            uiTracking.onAddNameOnRegistration(Left(entryError), SignInController.Phone)
//            Future.successful(Left(entryError))
//          case _ =>
//            uiTracking.onAddNameOnRegistration(Right(()), SignInController.Phone)
//            ZMessaging.currentAccounts.switchAccount(accountData.id).map(_ => Right(()))
//        }
//      case _ => Future.successful(Left(GenericRegisterPhoneError))
//    }
  }

  def resendActivationEmail(): Unit = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.getActiveAccount.map {
//      case Some(account) if account.pendingEmail.isDefined =>
//        ZMessaging.currentAccounts.requestVerificationEmail(account.pendingEmail.get)
//      case _ =>
//    }
  }

  def resendActivationPhoneCode(shouldCall: Boolean = false): Future[Either[EntryError, Unit]] = {
    throw new NotImplementedError("")
//
//    def requestCode(accountData: AccountData, kindOfAccess: KindOfAccess): Future[ActivateResult] = {
//      if (shouldCall)
//        ZMessaging.currentAccounts.requestPhoneConfirmationCall(accountData.pendingPhone.get, kindOfAccess).future
//      else
//        ZMessaging.currentAccounts.requestPhoneConfirmationCode(accountData.pendingPhone.get, kindOfAccess).future
//    }
//
//    ZMessaging.currentAccounts.getActiveAccount.flatMap {
//      case Some(account) if account.pendingPhone.isDefined =>
//         val kindOfAccess = if (account.regWaiting) KindOfAccess.REGISTRATION else KindOfAccess.LOGIN
//        requestCode(account, kindOfAccess).map {
//          case Failure(error) =>
//            Left(EntryError(error.code, error.label, SignInMethod(Register, Phone)))
//          case PasswordExists =>
//            val validationType = if (account.regWaiting) Register else Login
//            Left(EntryError(ErrorResponse.PasswordExists.code, ErrorResponse.PasswordExists.label, SignInMethod(validationType, Phone)))
//          case _ =>
//            Right(())
//        }
//      case _ => Future.successful(Left(GenericRegisterPhoneError))
//    }
  }

  def removeCurrentAccount(): Unit = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.logout(false)
  }

  def cancelEmailVerification(): Unit = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.updateCurrentAccount(_.copy(pendingEmail = None))
  }

  def setPicture(imageAsset: ImageAsset, source: SignUpPhotoFragment.Source, registrationType: RegistrationType): Unit = {
    throw new NotImplementedError("")
//    optZms.head.map {
//      case Some(zms) =>
//        zms.users.updateSelfPicture(imageAsset).map { _ =>
//          if (source != SignUpPhotoFragment.Source.Auto) {
//            val trackingSource = source match {
//              case SignUpPhotoFragment.Source.Unsplash => AddPhotoOnRegistrationEvent.Unsplash
//              case _ => AddPhotoOnRegistrationEvent.Gallery
//            }
//            val trackingRegType = registrationType match {
//              case SignUpPhotoFragment.RegistrationType.Email => Email
//              case SignUpPhotoFragment.RegistrationType.Phone => Phone
//            }
//            uiTracking.onAddPhotoOnRegistration(trackingRegType, trackingSource)
//          }
//        }
//      case _ =>
//    }
  }

  def requestTeamEmailVerificationCode(email: String): Future[Either[Unit, ErrorResponse]] = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.requestActivationCode(EmailAddress(email)).map {
//      case Right(()) => Left(())
//      case Left(e) => Right(e)
//    }
  }

  def resendTeamEmailVerificationCode(): ErrorOr[Unit] = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.getActiveAccount.flatMap {
//      case Some(accountData) if accountData.pendingEmail.isDefined =>
//        ZMessaging.currentAccounts.requestActivationCode(accountData.pendingEmail.get)
//      case _ =>
//        Future.successful(Right(ErrorResponse.internalError("No pending email or account")))
//    }
  }

  def setEmailVerificationCode(code: String, copyPaste: Boolean = false): ErrorOr[Unit] = {
    throw new NotImplementedError("")
//    import TeamsEnteredVerification._
//    ZMessaging.currentAccounts.verify(ConfirmationCode(code)).map { resp =>
//      val err = resp.fold(e => Some((e.code, e.label)), _ => None)
//      tracking.track(TeamsEnteredVerification(if (copyPaste) CopyPaste else Manual, err))
//      if (err.isEmpty) tracking.track(TeamVerified())
//      resp
//    }
  }

  def setName(name: String): ErrorOr[Unit] = {
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.updateCurrentAccount(_.copy(name = Some(name))).map(_ => Right(()))
  }

  def setPassword(password: String): ErrorOr[Unit] =
    throw new NotImplementedError("")
//    ZMessaging.currentAccounts.updateCurrentAccount(_.copy(password = Some(password))) flatMap { _ =>
//      ZMessaging.currentAccounts.register().map { resp =>
//        if (resp.isRight) tracking.track(TeamCreated())
//        resp
//      }
//    }

  def setUsername(username: String): ErrorOr[Unit] =
    throw new NotImplementedError("")
//    ZMessaging.accountsService.flatMap(_.getActiveAccountManager)
//      .collect { case Some(acc) => acc }
//      .flatMap(_.updateHandle(Handle(username)))

  def skipInvitations(): Unit =
    throw new NotImplementedError("")
//  ZMessaging.accountsService.flatMap(_.updateCurrentAccount(_.copy(pendingTeamName = None)))

}

object AppEntryController {
  trait FirstStage {
    val depth: Int = 0
  }
  object FirstScreen extends FirstStage
  object RegisterTeamScreen extends FirstStage { override val depth = 1 }
  object LoginScreen extends FirstStage { override val depth = 1 }

  trait AppEntryStage {
    val depth: Int = 0
  }
  object Unknown             extends AppEntryStage
  object Waiting             extends AppEntryStage
  object EnterAppStage       extends AppEntryStage
  object FirstEnterAppStage  extends AppEntryStage
  object DeviceLimitStage    extends AppEntryStage
  object AddNameStage        extends AppEntryStage
  object AddPictureStage     extends AppEntryStage
  object VerifyEmailStage    extends AppEntryStage
  object VerifyPhoneStage    extends AppEntryStage
  object AddHandleStage      extends AppEntryStage
  object InsertPasswordStage extends AppEntryStage
  object AddEmailStage       extends AppEntryStage

  object SetTeamEmail            extends AppEntryStage { override val depth = 2 }
  object VerifyTeamEmail         extends AppEntryStage { override val depth = 3 }
  object SetUsersNameTeam        extends AppEntryStage { override val depth = 4 }
  object SetPasswordTeam         extends AppEntryStage { override val depth = 5 }
  object SetUsernameTeam         extends AppEntryStage { override val depth = 6 }
  object TeamSetPicture          extends AppEntryStage { override val depth = 6 }
  object InviteToTeam            extends AppEntryStage { override val depth = 7 }
  case class NoAccountState(page: FirstStage) extends AppEntryStage { override val depth = page.depth }
}
