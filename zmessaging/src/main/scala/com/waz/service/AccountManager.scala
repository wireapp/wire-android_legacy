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

import java.io._
import java.util.Locale

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.ZmsVersion
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.content.UserPreferences._
import com.waz.model.AccountData.Password
import com.waz.model._
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.UserService.UnsplashUrl
import com.waz.service.assets.AssetService.RawAssetInput.UriInput
import com.waz.service.otr.OtrService.sessionId
import com.waz.service.tracking.LoggedOutEvent
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.waz.sync.client.{InvitationClientImpl, OtrClientImpl}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.Context
import com.waz.znet2.http.ResponseCode
import com.waz.sync.client.ErrorOr
import com.waz.sync.client.ErrorOrResponse
import com.waz.znet2.AuthRequestInterceptor

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Right

class AccountManager(val userId:   UserId,
                     val teamId:   Option[TeamId],
                     val global:   GlobalModule,
                     val accounts: AccountsService,
                     val startedJustAfterBackup: Boolean,
                     initialSelf: Option[UserInfo],
                     isLogin:     Option[Boolean]) {
  import AccountManager._

  implicit val dispatcher = new SerialDispatchQueue()
  implicit val accountContext: AccountContext = new AccountContext(userId, accounts)
  verbose(s"Creating for: $userId, team: $teamId, initialSelf: $initialSelf, startJustAfterBackup: $startedJustAfterBackup, isLogin: $isLogin")

  private def doAfterBackupCleanup() =
    Future.traverse(List(
      SelfClient.str,
      OtrLastPrekey.str,
      ClientRegVersion.str,
      LastSelfClientsSyncRequestedTime.str,
      LastStableNotification.str,
      ShouldSyncInitial.str
    ))(userPrefs.remove).map(_ => ())

  val storage   = global.factory.baseStorage(userId)
  val db        = storage.db
  val userPrefs = storage.userPrefs

  val account     = global.accountsStorage.signal(userId)
  val clientState = for {
    _ <- if (startedJustAfterBackup) Signal.future(doAfterBackupCleanup()) else Signal.const(())
    state <- userPrefs(SelfClient).signal
  } yield state

  val clientId    = clientState.map(_.clientId)

  val context        = global.context
  val contextWrapper = Context.wrap(context)

  val cryptoBox         = global.factory.cryptobox(userId, storage)
  val auth              = global.factory.auth(userId)
  val authRequestInterceptor: AuthRequestInterceptor = new AuthRequestInterceptor(auth, global.httpClient)
  val otrClient         = new OtrClientImpl()(global.urlCreator, global.httpClient, authRequestInterceptor)
  val credentialsClient = global.factory.credentialsClient(global.urlCreator, global.httpClient, authRequestInterceptor)

  val timeouts       = global.timeouts
  val network        = global.network
  val lifecycle      = global.lifecycle
  val reporting      = global.reporting
  val tracking       = global.trackingService
  val clientsStorage = storage.otrClientsStorage

  val invitationClient = new InvitationClientImpl()(global.urlCreator, global.httpClient, authRequestInterceptor)
  val invitedToTeam = Signal(ListMap.empty[TeamInvitation, Option[Either[ErrorResponse, ConfirmedTeamInvitation]]])

  private val initSelf = for {
    _ <- initialSelf.fold2(Future.successful({}), u =>
      for {
        _ <- storage.userPrefs(CrashesAndAnalyticsRequestShown) := false //new login/registration, we need to ask for permission to send analytics
        _ <- storage.usersStorage.updateOrCreate(u.id, _.updated(u, withSearchKey = false), UserData(u, withSearchKey = false)) //no search key to avoid transliteration loading
        _ <- storage.assetsStorage.insertAll(u.picture.getOrElse(Seq.empty)) //TODO https://github.com/wireapp/android-project/issues/58
      } yield {})
    _ <- isLogin.fold2(Future.successful({}), storage.userPrefs(IsLogin) := _)
  } yield {}

  val zmessaging: Future[ZMessaging] = {
    for {
      _       <- initSelf
      cId     <- clientId.collect { case Some(id) => id }.head
      Some(_) <- checkCryptoBox()
    } yield {
      verbose(s"Creating new ZMessaging instance for $userId, $cId, $teamId, service: $this")
      global.factory.zmessaging(teamId, cId, this, storage, cryptoBox)
    }
  }

  if (isLogin.contains(false)) addUnsplashPicture()

  if (startedJustAfterBackup) {
    zmessaging.foreach(_.tracking.historyRestored(true))
  }

  private val otrClients =
    storage.otrClientsStorage.signal(userId)
      .map(_.clients.values.toSet)
      .orElse(Signal.const(Set.empty[Client]))

  // listen to client changes, logout and delete cryptobox if current client is removed
  private val otrCurrentClient = clientId.flatMap {
    case Some(cId) => otrClients.map(_.find(_.id == cId))
    case _ => Signal const Option.empty[Client]
  }
  private var hasClient = false
  otrCurrentClient.map(_.isDefined) { exists =>
    if (hasClient && !exists) {
      info(s"client has been removed on backend, logging out")
      global.trackingService.loggedOut(LoggedOutEvent.RemovedClient, userId)
      logoutAndResetClient()
    }
    hasClient = exists
  }

  def addUnsplashPicture(): Future[Unit] = zmessaging.flatMap(_.users.updateSelfPicture(UriInput(UnsplashUrl)))

  def fingerprintSignal(uId: UserId, cId: ClientId): Signal[Option[Array[Byte]]] =
    for {
      selfClientId <- clientId
      fingerprint  <-
        if (userId == uId && selfClientId.contains(cId))
          Signal.future(cryptoBox(Future successful _.getLocalFingerprint))
        else
          cryptoBox.sessions.remoteFingerprint(sessionId(uId, cId))
    } yield fingerprint

  def getOrRegisterClient(): ErrorOr[ClientRegistrationState] = {
    verbose(s"registerClient()")

    def getSelfClients: ErrorOr[Unit] = {
      for {
        resp <- otrClient.loadClients().future
        _    <- resp.fold(_ => Future.successful({}), cs => clientsStorage.updateClients(Map(userId -> cs), replace = true))
      } yield resp.fold(err => Left(err), _ => Right({}))
    }

    Serialized.future("register-client", this) {
      clientState.head.flatMap {
        case st@Registered(_) =>
          verbose("Client already registered, returning")
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
  def registerNewClient(): ErrorOr[ClientRegistrationState] = {
    for {
      pw       <- account.map(_.password).head
      client   <- cryptoBox.createClient()
      resp <- client match {
        case None => Future.successful(Left(internalError("CryptoBox missing")))
        case Some((c, lastKey, keys)) =>
          otrClient.postClient(userId, c, lastKey, keys, pw).future.flatMap {
            case Right(cl) =>
              for {
                _    <- userPrefs(ClientRegVersion) := ZmsVersion.ZMS_MAJOR_VERSION
                _    <- clientsStorage.updateClients(Map(userId -> Seq(c.copy(id = cl.id).updated(cl))))
              } yield Right(Registered(cl.id))
            case Left(ErrorResponse(ResponseCode.Forbidden, _, "missing-auth"))     => Future.successful(Right(PasswordMissing))
            case Left(ErrorResponse(ResponseCode.Forbidden, _, "too-many-clients")) => Future.successful(Right(LimitReached))
            case Left(error)                                                  => Future.successful(Left(error))
          }
      }
    } yield resp
  }

  def deleteClient(id: ClientId, password: Password): ErrorOr[Unit] =
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

  def exportDatabase: Future[File] = for {
    zms     <- zmessaging
    user    <- zms.users.selfUser.head
    _       <- db.flushWALToDatabase()
    backup  = BackupManager.exportDatabase(
      userId,
      userHandle  = user.handle.map(_.string).getOrElse(""),
      databaseDir = context.getDatabasePath(userId.str).getParentFile,
      targetDir   = context.getExternalCacheDir
    )
    _       = tracking.historyBackedUp(backup.isSuccess)
  } yield backup.get

  private def checkCryptoBox() =
    cryptoBox.cryptoBox.flatMap {
      case Some(cb) => Future.successful(Some(cb))
      case None => logoutAndResetClient().map(_ => None)
    }

  private def logoutAndResetClient() =
    for {
      _ <- accounts.logout(userId)
      _ <- cryptoBox.deleteCryptoBox()
      _ <- userPrefs(SelfClient) := Unregistered
    } yield ()

  def setEmail(email: EmailAddress): ErrorOr[Unit] = {
    verbose(s"setEmail: $email")
    credentialsClient.updateEmail(email).future
  }

  def setPassword(password: Password): ErrorOr[Unit] = {
    verbose(s"setPassword: $password")
    credentialsClient.updatePassword(password, None).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_) => global.accountsStorage.update(userId, _.copy(password = Some(password))).map(_ => Right({}))
    }
  }

  //TODO should only have one request at a time?
  def checkEmailActivation(email: EmailAddress): ErrorOrResponse[Unit] = {
    verbose(s"checkEmailActivation $email")
    CancellableFuture.lift(updateSelf).flatMap {
      case Right(user) if !user.email.contains(email) => CancellableFuture.delay(3.seconds).flatMap(_ => checkEmailActivation(email))
      case Right(_)                                   => CancellableFuture.successful(Right({}))
      case Left(err)                                  => CancellableFuture.successful(Left(err))
    }
  }

  def hasPassword(): ErrorOrResponse[Boolean] = credentialsClient.hasPassword()

  def hasMarketingConsent: Future[Boolean] = {
    verbose("hasMarketingConsent")
    credentialsClient.hasMarketingConsent.map {
      case Right(result) => result
      case Left(err) =>
        verbose(s"Error while getting hasMarketingConsent: $err")
        false
    }.future
  }

  //receiving = None will set a preference so the app knows to ask again
  def setMarketingConsent(receiving: Option[Boolean]): ErrorOr[Unit] = {
    verbose(s"setMarketingConsent: $receiving")
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
