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

import java.io.File

import com.waz.api._
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences._
import com.waz.content.AccountStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.InternalLog
import com.waz.log.LogSE._
import com.waz.log.LogShow.SafeToLog
import com.waz.model.AccountData.Password
import com.waz.model._
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.sync.client.LoginClient.LoginResult
import com.waz.sync.client.{ErrorOr, LoginClient}
import com.waz.threading.Threading
import com.wire.signals.{EventStream, Serialized, Signal}
import com.waz.utils.{returning, _}

import scala.concurrent.Future
import scala.reflect.io.Directory
import scala.util.{Right, Try}

/**
  * There are a few possible states that an account can progress through for the purposes of log in and registration.
  *
  * No state - an account is not known to sync engine
  *
  * Logged in (global db row)  - the account has a cookie and token and can authenticate requests - it will be persisted,
  *   but logged in accounts alone are not visible externally to this service.
  *
  * With AccountManager - the account has a database as well as being logged in. Here, we can start registering clients
  *
  * With ZMessaging - A ready, working account with database, client and logged in.
  *
  * Active - the current selected account, this state is independent to the other states, except that the account in question
  *   must have an account manager
  *
  */
trait AccountsService {
  type HasOtherClients = Boolean
  type HadDB = Boolean

  import AccountsService._

  def requestVerificationEmail(email: EmailAddress): ErrorOr[Unit]

  def requestPhoneCode(phone: PhoneNumber, login: Boolean, call: Boolean = false): ErrorOr[Unit]
  def requestEmailCode(email: EmailAddress): ErrorOr[Unit]

  def verifyPhoneNumber(phone: PhoneNumber, code: ConfirmationCode, dryRun: Boolean): ErrorOr[Unit]
  def verifyEmailAddress(email: EmailAddress, code: ConfirmationCode, dryRun: Boolean = true): ErrorOr[Unit]

  def loginEmail(validEmail: String, validPassword: String): ErrorOr[UserId] = login(EmailCredentials(EmailAddress(validEmail), Password(validPassword)))
  def loginPhone(phone: String, code: String): ErrorOr[UserId] = login(PhoneCredentials(PhoneNumber(phone), ConfirmationCode(code)))
  def ssoLogin(userId: UserId, cookie: Cookie): Future[Either[ErrorResponse, (HasOtherClients, HadDB)]]
  def login(loginCredentials: Credentials): ErrorOr[UserId]

  def register(registerCredentials: Credentials, name: Name, teamName: Option[Name] = None): ErrorOr[Option[AccountManager]]

  def createAccountManager(userId: UserId,
                           isLogin: Option[Boolean],
                           initialUser: Option[UserInfo] = None): Future[Option[AccountManager]] //TODO return error codes on failure?

  //Set to None in order to go to the login screen without logging out the current users
  def setAccount(userId: Option[UserId]): Future[Unit]

  def logout(userId: UserId, reason: LogoutReason): Future[Unit]
  def onAccountLoggedOut: EventStream[(UserId, LogoutReason)]

  def accountManagers: Signal[Set[AccountManager]]
  def accountsWithManagers: Signal[Set[UserId]] = accountManagers.map(_.map(_.userId))
  def zmsInstances: Signal[Set[ZMessaging]]
  def getZms(userId: UserId): Future[Option[ZMessaging]]

  def accountState(userId: UserId): Signal[AccountState]

  def activeAccountId:                Signal[Option[UserId]]
  def activeAccount:                  Signal[Option[AccountData]]

  /**
   * @return a Signal of Boolean that informs if the active account has a SSO Subject (i.e. the
   *         account's password is managed by SSO and we should never ask the user for password confirmation).
   *         It doesn't mean that the account isn't managed by SSO at all. For that, see [[activeAccountUsesCompanyLogin]].
   */
  def activeAccountHasCompanyManagedPassword: Signal[Boolean] = activeAccount.map(_.exists(_.ssoId.exists(_.subject.nonEmpty)))

  /**
   * @return a Signal of Boolean that informs if the active account is managed by SSO. It doesn't mean
   *         that the account doesn't have a password. For that, see [[activeAccountHasCompanyManagedPassword]].
   */
  def activeAccountUsesCompanyLogin: Signal[Boolean] = activeAccount.map(_.exists(_.ssoId.isDefined))

  def activeAccountManager:           Signal[Option[AccountManager]]
  def activeZms:                      Signal[Option[ZMessaging]]

  def loginClient: LoginClient

  def wipeDataForAllAccounts(): Future[Unit]

  lazy val accountPassword: Signal[Option[Password]] = activeAccount.map(_.flatMap(_.password)).disableAutowiring()
}

object AccountsService {

  val AccountManagersKey = "accounts-map"
  val DbFileExtensions = Seq("", "-wal", "-shm", "-journal")

  sealed trait AccountState extends SafeToLog

  case object LoggedOut extends AccountState

  trait Active extends AccountState
  case object InBackground extends Active
  case object InForeground extends Active

  sealed trait LogoutReason
  case object UserInitiated extends LogoutReason
  case object InvalidCookie extends LogoutReason
  case object InvalidCredentials extends LogoutReason
  case object ClientDeleted extends LogoutReason
  case object UserDeleted extends LogoutReason
  case object DataWiped extends LogoutReason

}

class AccountsServiceImpl(global: GlobalModule, kotlinLogoutEnabled: Boolean = false)
  extends AccountsService with DerivedLogTag {
  import AccountsService._
  import Threading.Implicits.Background

  val prefs         = global.prefs

  lazy val context       = global.context
  lazy val phoneNumbers  = global.phoneNumbers
  lazy val regClient     = global.regClient
  lazy val loginClient   = global.loginClient
  lazy val logsService   = global.logsService

  private val activeAccountPref = prefs(ActiveAccountPref)

  private val storage: AccountStorage = global.accountsStorage

  override val accountManagers = Signal[Set[AccountManager]]()

  //create account managers for all logged in accounts on app start, or initialise the signal to an empty set
  calculateAccountManagers()

  private var subscribeAccountPrefChanges = kotlinLogoutEnabled
  // Normally, accountManagers is mutated through logout() call in this class, so we don't need to
  // calculate it from scratch.
  // In Kotlin mode, the logout process is not handled by this class, so we need to update it
  // to notify the Scala dependants.
  activeAccountPref.signal.onChanged.on(Threading.Background) { _ =>
    if (subscribeAccountPrefChanges) calculateAccountManagers()
  }

  private def calculateAccountManagers() =
    (for {
      ids      <- storage.keySet
      managers <- Future.sequence(ids.map(createAccountManager(_, None, None)))
      _        <- Serialized.future(AccountManagersKey)(Future(accountManagers ! managers.flatten))
     } yield ()).recoverWith {
       case e : Exception =>
         error(l"error creating account managers $e")
         Future.failed(e)
     }

  override def createAccountManager(userId:      UserId,
                                    isLogin:     Option[Boolean],
                                    initialUser: Option[UserInfo] = None): Future[Option[AccountManager]] = Serialized.future(AccountManagersKey) {
    for {
      managers <- accountManagers.orElse(Signal.const(Set.empty[AccountManager])).head
      manager  <- managers.find(_.userId == userId)
                          .fold(createManager(userId, isLogin, initialUser))(m => Future.successful(Some(m)))
    } yield manager
  }

  private def createManager(userId: UserId, isLogin: Option[Boolean], initialUser: Option[UserInfo]): Future[Option[AccountManager]] = {
    for {
      account <- storage.get(userId)
      _       =  if (account.isEmpty) warn(l"No logged in account for user: $userId, not creating account manager")
      user    <- if (account.isDefined) prefs(LoggingInUser).apply().map(_.orElse(initialUser)) else Future.successful(None)
      _       <- if (account.isDefined) prefs(LoggingInUser) := None else Future.successful(())
    } yield account.map { acc =>
      val newManager = new AccountManager(userId, acc.domain, acc.teamId, global, this, user, isLogin)
      if (isLogin.isDefined) accountManagers.mutateOrDefault(_ + newManager, Set(newManager))
      newManager
    }
  }

  @volatile private var accountStateSignals = Map.empty[UserId, Signal[AccountState]]
  override def accountState(userId: UserId) = {

    lazy val newSignal: Signal[AccountState] =
      for {
        selected <- activeAccountPref.signal.map(_.contains(userId))
        loggedIn <- accountsWithManagers.map(_.contains(userId))
        uiActive <- global.lifecycle.uiActive
      } yield {
        returning(if (!loggedIn) LoggedOut else if (uiActive && selected) InForeground else InBackground) { state =>
          verbose(l"account state changed: $userId -> $state: selected: $selected, loggedIn: $loggedIn, uiActive: $uiActive")
        }
      }

    accountStateSignals.getOrElse(userId, returning(newSignal) { sig =>
      accountStateSignals += userId -> sig
    })
  }

  override val activeAccountManager: Signal[Option[AccountManager]] = activeAccountPref.signal.flatMap[Option[AccountManager]] {
    case Some(id) => accountManagers.map(_.find(_.userId == id))
    case None     => Signal.const(None)
  }

  override lazy val activeAccount: Signal[Option[AccountData]] = activeAccountManager.flatMap[Option[AccountData]] {
    case Some(am) => storage.optSignal(am.userId)
    case None     => Signal.const(None)
  }

  override lazy val activeAccountId: Signal[Option[UserId]] = activeAccount.map(_.map(_.id))

  override val activeZms: Signal[Option[ZMessaging]] = activeAccountManager.flatMap[Option[ZMessaging]] {
    case Some(am) => Signal.from(am.zmessaging.map(Some(_)))
    case None     => Signal.const(None)
  }

  override lazy val zmsInstances: Signal[Set[ZMessaging]] = (for {
    ams <- accountManagers
    zs  <- Signal.sequence(ams.map(am => Signal.from(am.zmessaging)).toSeq: _*)
  } yield
    returning(zs.toSet) { v =>
      verbose(l"Loaded: ${v.size} zms instances for ${ams.size} accounts")
    }).disableAutowiring()

  override def getZms(userId: UserId): Future[Option[ZMessaging]] =
    activeZms.currentValue.flatten match {
      case Some(zms) if zms.selfUserId == userId =>
        Future.successful(Some(zms)) // an optimization - maybe we don't need to initialize the other account
      case _ =>
        zmsInstances.head.map(_.find(_.selfUserId == userId))
    }

  lazy val onAccountLoggedOut = EventStream[(UserId, LogoutReason)]()

  //TODO optional delete history
  def logout(userId: UserId, reason: LogoutReason): Future[Unit] = {
    verbose(l"logout: $userId")
    for {
      isLoggedIn    <- storage.get(userId).map(_.isDefined) if isLoggedIn
      current       <- activeAccountId.head
      otherAccounts <- accountsWithManagers.head.map(_.filter(userId != _))
      _             <- if (current.contains(userId)) setAccount(otherAccounts.headOption) else Future.successful(())
      _             <- storage.remove(userId)
    } yield {
      verbose(l"user logged out: $userId. Reason: $reason")
      Serialized.future(AccountManagersKey)(Future[Unit](accountManagers.mutate(_.filterNot(_.userId == userId))))
      onAccountLoggedOut ! (userId -> reason)
    }
  }

  /**
    * Switches the current account to the given user id. If the other account cannot be authorized
    * (no cookie) or if anything else goes wrong, we leave the user logged out.
    */
  override def setAccount(userId: Option[UserId]): Future[Unit] = userId match {
    case Some(id) =>
      activeAccountId.head.flatMap {
        case Some(cur) if cur == id => Future.successful({})
          case Some(_)   => accountManagers.head.map(_.find(_.userId == id)).flatMap {
            case Some(_) => updateActiveAccountPref(Some(id))
            case _ =>
              warn(l"Tried to set active user who is not logged in: $userId, not changing account")
              Future.successful({})
          }
          case _ => updateActiveAccountPref(Some(id))
        }
    case None => updateActiveAccountPref(None)
  }

  def requestVerificationEmail(email: EmailAddress): ErrorOr[Unit] =
    regClient.requestVerificationEmail(email)

  override def requestPhoneCode(phone: PhoneNumber, login: Boolean, call: Boolean = false): ErrorOr[Unit] =
    phoneNumbers.normalize(phone).flatMap { normalizedPhone =>
      regClient.requestPhoneCode(normalizedPhone.getOrElse(phone), login, call)
    }

  override def requestEmailCode(email: EmailAddress): ErrorOr[Unit] =
    regClient.requestEmailCode(email)

  override def verifyPhoneNumber(phone: PhoneNumber, code: ConfirmationCode, dryRun: Boolean): ErrorOr[Unit] =
    phoneNumbers.normalize(phone).flatMap { normalizedPhone =>
      regClient.verifyRegistrationMethod(Left(normalizedPhone.getOrElse(phone)), code, dryRun).map(_.fold(Left(_), _ => Right({})))
      //TODO handle label and cookie!(https://github.com/wireapp/android-project/issues/51)
    }

  override def verifyEmailAddress(email: EmailAddress, code: ConfirmationCode, dryRun: Boolean = true): ErrorOr[Unit] = {
    regClient.verifyRegistrationMethod(Right(email), code, dryRun).map(_.fold(Left(_), _ => Right({})))
    //TODO handle label and cookie! (https://github.com/wireapp/android-project/issues/51)
  }

  override def login(loginCredentials: Credentials): ErrorOr[UserId] =
    loginClient.login(loginCredentials).flatMap {
      case Right(LoginResult(token, Some(cookie), _)) => //TODO handle label (https://github.com/wireapp/android-project/issues/51)
        loginClient.getSelfUserInfo(token).flatMap {
          case Right(user) => for {
            _ <- addAccountEntry(user, cookie, Some(token), Some(loginCredentials))
            _ <- prefs(LoggingInUser) := Some(user)
          } yield Right(user.id)
          case Left(err)   => Future.successful(Left(err))
        }
      case Right(_) =>
        warn(l"login didn't return with a cookie, aborting")
        Future.successful(Left(ErrorResponse.internalError("No cookie for user after login - can't create account")))
      case Left(error) =>
        verbose(l"login failed: $error")
        Future.successful(Left(error))
    }

  override def register(registerCredentials: Credentials, name: Name, teamName: Option[Name] = None): ErrorOr[Option[AccountManager]] =
    regClient.register(registerCredentials, name, teamName).flatMap {
      case Right((user, Some((cookie, _)))) =>
        for {
          _  <- addAccountEntry(user, cookie, None, Some(registerCredentials))
          am <- createAccountManager(user.id, isLogin = Some(false), initialUser = Some(user))
          _  <- am.fold(Future.successful({}))(_.getOrRegisterClient().map(_ => ()))
          _  <- setAccount(Some(user.id))
        } yield Right(am)
      case Right(_) =>
        warn(l"Register didn't return a cookie")
        Future.successful(Left(ErrorResponse.internalError("No cookie for user after registration - can't create account")))
      case Left(error) =>
        verbose(l"register failed: $error")
        Future.successful(Left(error))
    }

  private def addAccountEntry(user: UserInfo, cookie: Cookie, token: Option[AccessToken], credentials: Option[Credentials]): Future[Unit] =
    storage.updateOrCreate(
      user.id,
      _.copy(
        cookie      = cookie,
        domain      = user.domain,
        accessToken = token,
        password    = credentials.flatMap(_.maybePassword),
        ssoId       = user.ssoId
      ),
      AccountData(user.id, user.domain, user.teamId, cookie, token, password = credentials.flatMap(_.maybePassword), ssoId = user.ssoId)
    ).map(_ => {})

  override def ssoLogin(userId: UserId, cookie: Cookie): Future[Either[ErrorResponse, (HasOtherClients, HadDB)]] =
    loginClient.access(cookie, None).flatMap {
      case Right(loginResult) =>
        loginClient.getSelfUserInfo(loginResult.accessToken).flatMap {
          case Right(userInfo) =>
            for {
              _     <- addAccountEntry(userInfo, cookie, Some(loginResult.accessToken), None)
              hadDb =  context.getDatabasePath(userId.str).exists
              am    <- createAccountManager(userId, isLogin = Some(true), initialUser = Some(userInfo))
              r     <- am.fold2(Future.successful(Left(ErrorResponse.internalError(""))), _.otrClient.loadClients().future.mapRight(cs => (cs.nonEmpty, hadDb)))
              _     =  r.fold(_ => (), res => if (!res._1) am.foreach(_.addUnsplashIfProfilePictureMissing()))
            } yield r
          case Left(error) =>
            verbose(l"login - Get self error: $error")
            Future.successful(Left(error))
        }
      case Left(error) =>
        verbose(l"login - access error: $error")
        Future.successful(Left(error))
    }

  override def wipeDataForAllAccounts(): Future[Unit] = {
    def delete(file: File) =
      if (file.exists) Try(file.delete()).isSuccess else true

    //wrap everything in Try blocks as otherwise exceptions might cause us to skip future wiping
    //operations and the logout call
    val deleteDbFiles = Try(databaseDir().foreach(delete))
    if(deleteDbFiles.isFailure) error(l"failed to wipe db files", deleteDbFiles.failed.get)

    val deleteOtrFiles = Try(otrFilesDir().deleteRecursively())
    if(deleteOtrFiles.isFailure) {
      error(l"Got exception when attempting to delete otr files", deleteOtrFiles.failed.get)
    } else if(deleteOtrFiles.isSuccess && !deleteOtrFiles.get) {
      error(l"Failed to delete otr files")
    }

    val deleteCacheDir = Try(cacheDir().deleteRecursively())
    if(deleteCacheDir.isFailure) {
      warn(l"Failed to delete cache dir, skipping...")
    }

    val deleteLogs = Try(clearLogs())
    if(deleteLogs.isFailure) {
      warn(l"Failed to delete logs, skipping...")
    }

    for {
      accIds <- zmsInstances.head.map(_.map(_.selfUserId))
    } yield Future.sequence(accIds.map(id => logout(id, DataWiped)))
  }

  private def databaseDir(): Set[File] = {
    val databaseDir = s"${context.getApplicationInfo.dataDir}/databases"
    new File(databaseDir).listFiles().filter(_.isFile).toSet
  }

  private def otrFilesDir(): Directory =
    new Directory(new File(s"${context.getApplicationInfo.dataDir}/files/otr/"))

  private def cacheDir(): Directory = new Directory(context.getCacheDir)

  private def clearLogs(): Future[Unit] = {
    //disable logging so we don't write new logs after clearing, but before logging out
    //this does mean we are clearing the logs twice, but if we don't call it explicitly, there is
    //a potential race condition between us clearing the logs and logging out
    returning(logsService.setLogsEnabled(false)) { _ => InternalLog.clearAll()}
  }

  private def updateActiveAccountPref(newPref: Option[UserId]): Future[Unit] =
    if (!kotlinLogoutEnabled) {
      activeAccountPref := newPref
    } else {
      //let's not retrigger ourselves for no reason
      subscribeAccountPrefChanges = false
      (activeAccountPref := newPref).map(_ => subscribeAccountPrefChanges = true)
    }
}
