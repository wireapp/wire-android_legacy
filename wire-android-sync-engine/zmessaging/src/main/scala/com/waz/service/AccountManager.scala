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

import java.util.Locale

import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AccountData.Password
import com.waz.model._
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.UserService.UnsplashUrl
import com.waz.service.AccountsService.ClientDeleted
import com.waz.service.assets.Content
import com.waz.service.otr.OtrService.SessionId
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.waz.sync.client.{ErrorOr, ErrorOrResponse, InvitationClientImpl, OtrClientImpl}
import com.wire.signals.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils._
import com.wire.signals._
import com.waz.utils.wrappers.URI
import com.waz.znet2.http.ResponseCode
import com.waz.znet2.{AuthRequestInterceptor, AuthRequestInterceptorImpl}

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Right

class AccountManager(val userId:  UserId,
                     val teamId:  Option[TeamId],
                     val global:  GlobalModule,
                     accounts:    AccountsService,
                     initialSelf: Option[UserInfo],
                     isLogin:     Option[Boolean]) extends DerivedLogTag {
  import AccountManager._

  private implicit val dispatcher = SerialDispatchQueue(name = "AccountManager")
  implicit val accountContext: AccountContext = new AccountContext(userId, accounts)
  verbose(l"Creating for: $userId, team: $teamId, initialSelf: $initialSelf, isLogin: $isLogin")

  lazy val storage   = global.factory.baseStorage(userId)
  lazy val userPrefs = storage.userPrefs

  lazy val clientState = userPrefs(SelfClient).signal

  lazy val clientId                                       = clientState.map(_.clientId)
  private lazy val cryptoBox                              = global.factory.cryptobox(userId, storage)
  lazy val auth                                           = global.factory.auth(userId)
  lazy val authRequestInterceptor: AuthRequestInterceptor = new AuthRequestInterceptorImpl(auth, global.httpClient)
  lazy val otrClient                                      = new OtrClientImpl()(global.urlCreator, global.httpClient, authRequestInterceptor)
  lazy val credentialsClient                              = global.factory.credentialsClient(global.urlCreator, global.httpClient, authRequestInterceptor)

  private lazy val clientsStorage = storage.otrClientsStorage

  lazy val invitationClient = new InvitationClientImpl()(global.urlCreator, global.httpClient, authRequestInterceptor)
  val invitedToTeam = Signal(ListMap.empty[TeamInvitation, Option[Either[ErrorResponse, ConfirmedTeamInvitation]]])

  private lazy val initSelf = for {
    _ <- initialSelf.fold2(Future.successful({}), u =>
      for {
        _ <- storage.userPrefs(CrashesAndAnalyticsRequestShown) := false //new login/registration, we need to ask for permission to send analytics
        _ <- storage.usersStorage.updateOrCreate(
               u.id,
               _.updated(u, withSearchKey = false, permissions = (0L, 0L)),
               UserData(u, withSearchKey = false)
             ) //no search key to avoid transliteration loading
      } yield {})
    _ <- isLogin.fold2(Future.successful({}), storage.userPrefs(IsLogin) := _)
  } yield {}

  lazy val zmessaging: Future[ZMessaging] = {
    for {
      _       <- initSelf
      cId     <- clientId.collect { case Some(id) => id }.head
      Some(_) <- checkCryptoBox()
    } yield {
      global.factory.zmessaging(teamId, cId, this, storage, cryptoBox)
    }
  }

  private lazy val otrClients =
    storage.otrClientsStorage.signal(userId)
      .map(_.clients.values.toSet)
      .orElse(Signal.const(Set.empty[Client]))

  // listen to client changes, logout and delete cryptobox if current client is removed
  private lazy val otrCurrentClient = clientId.flatMap {
    case Some(cId) => otrClients.map(_.find(_.id == cId))
    case _ => Signal const Option.empty[Client]
  }

  private var hasClient = false
  otrCurrentClient.map(_.isDefined) { exists =>
    if (hasClient && !exists) {
      info(l"client has been removed on backend, logging out")
      logoutAndResetClient()
    }
    hasClient = exists
  }

  def initZMessaging(): Unit = {
    zmessaging
  }

  def addUnsplashPicture(): Future[Unit] = zmessaging.flatMap(_.users.updateSelfPicture(Content.Uri(URI.toJava(UnsplashUrl))))

  def fingerprintSignal(uId: UserId, cId: ClientId): Signal[Option[Array[Byte]]] =
    for {
      selfClientId <- clientId
      fingerprint  <-
        if (userId == uId && selfClientId.contains(cId))
          Signal.from(cryptoBox(Future successful _.getLocalFingerprint))
        else
          cryptoBox.sessions.remoteFingerprint(SessionId(uId, cId))
    } yield fingerprint

  def getOrRegisterClient(): ErrorOr[ClientRegistrationState] = {
    verbose(l"registerClient()")

    def getSelfClients: ErrorOr[Unit] = {
      for {
        resp <- otrClient.loadClients().future
        _    <- resp.fold(_ => Future.successful({}), cs => clientsStorage.updateClients(Map(userId -> cs), replace = true))
      } yield resp.fold(err => Left(err), _ => Right({}))
    }

    Serialized.future(s"register-client $userId") {
      clientState.head.flatMap {
        case st@Registered(_) =>
          verbose(l"Client already registered, returning")
          Future.successful(Right(st))
        case _ =>
          for {
            r1 <- registerNewClient()
            r2 <- r1 match {
              case Right(r@Registered(_)) =>
                for {
                  isLogin <- userPrefs(IsLogin).apply()
                  _       <- if (isLogin) userPrefs(IsNewClient) := true else Future.successful({})
                } yield Right(r)
              case Right(LimitReached) => getSelfClients.map(_.fold(e => Left(e), _ => Right(LimitReached)))
              case Right(st)           => Future.successful(Right(st))
              case Left(e)             => Future.successful(Left(e))
            }
            _ <- r2.fold(_ => Future.successful({}), userPrefs(SelfClient) := _)
          } yield r2
      }
    }
  }

  //Note: this method should only be externally called from tests and debug preferences. User `registerClient` for all normal flows.
  def registerNewClient(password: Option[Password] = None): ErrorOr[ClientRegistrationState] = {
    for {
      account <- global.accountsStorage.signal(userId).head
      pwd     = password.orElse(if (account.ssoId.isEmpty) account.password else None)
      client  <- cryptoBox.createClient()
      resp    <- client match {
        case None => Future.successful(Left(internalError("CryptoBox missing")))
        case Some((c, lastKey, keys)) =>
          otrClient.postClient(userId, c, lastKey, keys, pwd).future.flatMap {
            case Right(cl) =>
              verbose(l"new client: $cl")
              clientsStorage.updateClients(Map(userId -> Seq(c.copy(id = cl.id).updated(cl))))
                .map(_ => Right(Registered(cl.id)))
            case Left(ErrorResponse(ResponseCode.Forbidden, _, "missing-auth"))     =>
              verbose(l"missing auth")
              Future.successful(Right(PasswordMissing))
            case Left(ErrorResponse(ResponseCode.Forbidden, _, "too-many-clients")) => Future.successful(Right(LimitReached))
            case Left(error)                                                  => Future.successful(Left(error))
          }
      }
    } yield resp
  }

  def deleteClient(id: ClientId, password: Option[Password]): ErrorOr[Unit] =
    clientsStorage.get(userId).flatMap {
      case Some(cs) if cs.clients.contains(id) =>
        otrClient.deleteClient(id, password).future.flatMap {
          case Right(_) => for {
            _ <- clientsStorage.update(userId, { uc => uc.copy(clients = uc.clients - id) })
          } yield Right(())
          case res => Future.successful(res)
        }
      case _ => Future.successful(Left(internalError("Client does not belong to current user or was already deleted")))
    }

  def inviteToTeam(emailAddress: EmailAddress, name: Option[String], locale: Option[Locale] = None): ErrorOrResponse[ConfirmedTeamInvitation] =
    teamId match {
      case Some(tid) =>
        val invitation = TeamInvitation(tid, emailAddress, name.getOrElse(" "), locale)
        invitationClient.postTeamInvitation(invitation).map {
          returning(_) { r =>
            if (r.isRight) invitedToTeam.mutate {
              _ ++ ListMap(invitation -> Some(r))
            }
          }
        }
      case None => CancellableFuture.successful(Left(internalError("Not a team account")))
    }

  def getSelf: Future[UserData] =
    initSelf.flatMap(_ => storage.usersStorage.get(userId)).collect { case Some(u) => u } //self should always be defined at this point

  def updateSelf(): ErrorOr[UserInfo] = {
    auth.currentToken().flatMap {
      case Right(token) => accounts.loginClient.getSelfUserInfo(token)
      case Left(err) => Future.successful(Left(err))
    }.flatMap {
      case Right(info) => storage.usersStorage.update(userId, _.updated(info)).map(_ => Right(info))
      case Left(err) => Future.successful(Left(err))
    }
  }

  private def checkCryptoBox() =
    cryptoBox.cryptoBox.flatMap {
      case Some(cb) => Future.successful(Some(cb))
      case None => logoutAndResetClient().map(_ => None)
    }

  private def logoutAndResetClient() =
    for {
      _ <- accounts.logout(userId, ClientDeleted)
      _ <- cryptoBox.deleteCryptoBox()
      _ <- userPrefs(SelfClient) := Unregistered
    } yield ()

  def setEmail(email: EmailAddress): ErrorOr[Unit] = {
    verbose(l"setEmail: $email")
    credentialsClient.updateEmail(email).future
  }

  def setPassword(password: Password): ErrorOr[Unit] = {
    verbose(l"setPassword: $password")
    credentialsClient.updatePassword(password, None).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_) => global.accountsStorage.update(userId, _.copy(password = Some(password))).map(_ => Right({}))
    }
  }

  //TODO should only have one request at a time?
  def checkEmailActivation(email: EmailAddress): ErrorOrResponse[Unit] = {
    verbose(l"checkEmailActivation $email")
    CancellableFuture.lift(updateSelf).flatMap {
      case Right(user) if !user.email.contains(email) => CancellableFuture.delay(3.seconds).flatMap(_ => checkEmailActivation(email))
      case Right(_)                                   => CancellableFuture.successful(Right({}))
      case Left(err)                                  => CancellableFuture.successful(Left(err))
    }
  }

  def hasPassword(): ErrorOrResponse[Boolean] = credentialsClient.hasPassword()

  def hasMarketingConsent: Future[Boolean] = {
    verbose(l"hasMarketingConsent")
    credentialsClient.hasMarketingConsent.map {
      case Right(result) => result
      case Left(err) =>
        verbose(l"Error while getting hasMarketingConsent: $err")
        false
    }.future
  }

  //receiving = None will set a preference so the app knows to ask again
  def setMarketingConsent(receiving: Option[Boolean]): ErrorOr[Unit] = {
    verbose(l"setMarketingConsent: $receiving")
    receiving match {
      case Some(v) =>
        val meta = global.metadata
        for {
          resp <- credentialsClient.setMarketingConsent(v, meta.majorVersion, meta.minorVersion).future
          _    <- resp match {
            case Right(_) => (userPrefs(AskMarketingConsentAgain) := false).map(_ => Right({}))
            case _ => Future.successful({})
          }
        } yield resp
      case _ =>
        (userPrefs(AskMarketingConsentAgain) := true).map(_ => Right({}))
    }

  }
}

object AccountManager {

  sealed trait ClientRegistrationState {
    val clientId: Option[ClientId] = None
  }

  object ClientRegistrationState {
    case object Unregistered    extends ClientRegistrationState
    case object PasswordMissing extends ClientRegistrationState
    case object LimitReached    extends ClientRegistrationState
    case class  Registered(cId: ClientId) extends ClientRegistrationState {
      override val clientId = Some(cId)
    }
  }

  val ActivationThrottling = new ExponentialBackoff(2.seconds, 15.seconds)
}
