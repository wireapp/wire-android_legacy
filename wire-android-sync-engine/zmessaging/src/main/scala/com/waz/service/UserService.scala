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

import com.waz.log.LogSE._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.content._
import com.waz.model.AccountData.Password
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{AccentColor, _}
import com.waz.service.AccountsService.UserDeleted
import com.waz.service.EventScheduler.Stage
import com.waz.service.UserSearchService.UserSearchEntry
import com.waz.service.UserService._
import com.waz.service.assets.{AssetService, AssetStorage, Content, ContentForUpload, NoEncryption}
import com.waz.service.conversation.SelectedConversationService
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.client.{CredentialsUpdateClient, ErrorOr, UsersClient}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals._
import com.waz.utils.wrappers.AndroidURIUtil

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Right

trait UserService {
  def userUpdateEventsStage: Stage.Atomic
  def userDeleteEventsStage: Stage.Atomic

  def deleteUsers(ids: Set[UserId]): Future[Unit]

  def selfUser: Signal[UserData]
  def currentConvMembers: Signal[Set[UserId]]
  def userNames: Signal[Map[UserId, Name]]

  def getSelfUser: Future[Option[UserData]]
  def findUser(id: UserId): Future[Option[UserData]]
  def getOrCreateUser(id: UserId): Future[UserData]
  def updateUserData(id: UserId, updater: UserData => UserData): Future[Option[(UserData, UserData)]]
  def syncIfNeeded(userIds: Set[UserId], olderThan: FiniteDuration = SyncIfOlderThan): Future[Option[SyncId]]
  def updateConnectionStatus(id: UserId, status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None): Future[Option[UserData]]
  def updateUsers(entries: Seq[UserSearchEntry]): Future[Set[UserData]]
  def syncRichInfoNowForUser(id: UserId): Future[Option[UserData]]
  def syncUser(userId: UserId): Future[Option[UserData]]
  def acceptedOrBlockedUsers: Signal[Map[UserId, UserData]]

  def updateSyncedUsers(users: Seq[UserInfo], timestamp: LocalInstant = LocalInstant.Now): Future[Set[UserData]]

  def deleteAccount(): Future[SyncId]

  //These self user properties can fail in many ways, so we do not sync them and force the user to respond
  def setEmail(email: EmailAddress, password: Password): ErrorOr[Unit]
  def updateEmail(email: EmailAddress): ErrorOr[Unit]
  def updatePhone(phone: PhoneNumber): ErrorOr[Unit]
  def clearPhone(): ErrorOr[Unit]
  def setPassword(password: Password): ErrorOr[Unit]
  def checkPassword(password: Password): ErrorOr[Unit]
  def changePassword(newPassword: Password, oldPassword: Password): ErrorOr[Unit]
  def updateHandle(handle: Handle): ErrorOr[Unit]

  //These self user properties should always succeed given no fatal errors, so we update locally and create sync jobs
  def updateName(name: Name): Future[Unit]
  def updateAccentColor(color: AccentColor): Future[Unit]
  def updateAvailability(availability: Availability): Future[Unit]

  def storeAvailabilities(availabilities: Map[UserId, Availability]): Future[Seq[(UserData, UserData)]]
  def updateSelfPicture(content: Content): Future[Unit]
}

class UserServiceImpl(selfUserId:        UserId,
                      teamId:            Option[TeamId],
                      accounts:          AccountsService,
                      accsStorage:       AccountStorage,
                      usersStorage:      UsersStorage,
                      membersStorage:    MembersStorage,
                      userPrefs:         UserPreferences,
                      push:              PushService,
                      assets:            AssetService,
                      usersClient:       UsersClient,
                      sync:              SyncServiceHandle,
                      assetsStorage:     AssetStorage,
                      credentialsClient: CredentialsUpdateClient,
                      selectedConv:      SelectedConversationService,
                      messages:          MessagesService
                     ) extends UserService with DerivedLogTag {

  import Threading.Implicits.Background

  private val shouldSyncUsers = userPrefs.preference(UserPreferences.ShouldSyncUsers)

  for {
    shouldSync <- shouldSyncUsers()
  } if (shouldSync) {
    verbose(l"Syncing user data to get team ids")
    usersStorage.list()
      .flatMap(users => sync.syncUsers(users.map(_.id).toSet))
      .flatMap(_ => shouldSyncUsers := false)
    }

  override val currentConvMembers = for {
    Some(convId) <- selectedConv.selectedConversationId
    membersIds   <- membersStorage.activeMembers(convId)
  } yield membersIds

  currentConvMembers(syncIfNeeded(_))

  override lazy val userNames: Signal[Map[UserId, Name]] = {
    val added = usersStorage.onAdded.map(_.map(user => user.id -> user.name).toMap)

    val updated = usersStorage.onUpdated.map(_.collect {
      case (o, n) if o.name != n.name => n.id -> n.name
    }.toMap).filter(_.nonEmpty)

    def initialLoad = usersStorage.list().map(_.map(user => user.id -> user.name).toMap)

    new AggregatingSignal[Map[UserId, Name], Map[UserId, Name]](
      EventStream.zip(added, updated),
      initialLoad,
      { (values, changes) => values ++ changes }
    )
  }

  //Update user data for other accounts
  accounts.accountsWithManagers.map(_ - selfUserId)(userIds => syncIfNeeded(userIds))

  override val selfUser: Signal[UserData] = usersStorage.optSignal(selfUserId) flatMap {
    case Some(data) => Signal.const(data)
    case None =>
      sync.syncSelfUser()
      Signal.empty
  }

  override val userUpdateEventsStage: Stage.Atomic = EventScheduler.Stage[UserUpdateEvent] { (_, e) =>
    val (removeEvents, updateEvents) = e.partition(_.removeIdentity)
    for {
      _ <- updateSyncedUsers(updateEvents.map(_.user))
      _ <- {
        val updaters = removeEvents.map(_.user).map { ui =>
          ui.id -> ((userData: UserData) => userData.copy(
            email = if (ui.email.nonEmpty) None else userData.email,
            phone = if (ui.phone.nonEmpty) None else userData.phone
          ))
        }.toMap

        usersStorage.updateAll(updaters)
      }
    } yield {}
  }

  override val userDeleteEventsStage: Stage.Atomic = EventScheduler.Stage[UserDeleteEvent] { (c, e) =>
    Future.sequence(e.map(event => accounts.logout(event.user, reason = UserDeleted))).flatMap { _ =>
      deleteUsers(e.map(_.user).toSet)
    }
  }

  override def deleteUsers(ids: Set[UserId]): Future[Unit] =
    for {
      members       <- membersStorage.getByUsers(ids)
      _             <- membersStorage.removeAll(members.map(_.id).toSet)
      memberInConvs =  members.groupBy(_.convId)
      _             <- Future.traverse(memberInConvs) {
                         case (convId, ms) => messages.addMemberLeaveMessage(convId, selfUserId, ms.map(_.userId).toSet)
                       }
      _             <- usersStorage.updateAll2(ids, _.copy(deleted = true))
    } yield ()

  override lazy val acceptedOrBlockedUsers: Signal[Map[UserId, UserData]] =
    new AggregatingSignal[Seq[UserData], Map[UserId, UserData]](
      usersStorage.onChanged, usersStorage.listUsersByConnectionStatus(AcceptedOrBlocked),
      { (accu, us) =>
        val (toAdd, toRemove) = us.partition(u => AcceptedOrBlocked(u.connection))
        accu -- toRemove.map(_.id) ++ toAdd.map(u => u.id -> u)
      }
    )

  override def findUser(id: UserId): Future[Option[UserData]] = usersStorage.get(id)

  override def getOrCreateUser(id: UserId) = usersStorage.getOrCreate(id, {
    sync.syncUsers(Set(id))
    UserData(id, None, Name.Empty, None, None, connection = ConnectionStatus.Unconnected, searchKey = SearchKey.Empty, handle = None)
  })

  override def updateConnectionStatus(id: UserId, status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None) =
    usersStorage.update(id, { _.updateConnectionStatus(status, time, message)}).map {
      case Some((prev, updated)) if prev != updated => Some(updated)
      case _ => None
    }

  override def updateUserData(id: UserId, updater: UserData => UserData) = usersStorage.update(id, updater)

  override def updateUsers(entries: Seq[UserSearchEntry]) = {
    def updateOrAdd(entry: UserSearchEntry) = (_: Option[UserData]).fold(UserData(entry))(_.updated(entry))
    usersStorage.updateOrCreateAll(entries.map(entry => entry.id -> updateOrAdd(entry)).toMap)
  }

  override def syncRichInfoNowForUser(id: UserId): Future[Option[UserData]] = Serialized.future(s"syncRichInfoNow $id") {
    usersClient.loadRichInfo(id).future.flatMap {
      case Right(f) =>
          updateUserData(id, u => u.copy(fields = f))
          .map(_.map(_._2))
      case Left(error) => Future.failed(error)
    }
  }

  override def syncUser(userId: UserId): Future[Option[UserData]] =
    usersClient.loadUser(userId).future.flatMap {
      case Left(e) =>
        Future.failed(e)
      case Right(Some(info)) =>
        updateSyncedUsers(Seq(info)).map(_.headOption)
      case Right(None) =>
        deleteUsers(Set(userId)).map(_ => None)
    }

  def syncSelfNow: Future[Option[UserData]] = Serialized.future(s"syncSelfNow $selfUserId") {
    usersClient.loadSelf().future.flatMap {
      case Right(info) =>
        updateSyncedUsers(Seq(info)) map { _.headOption }
      case Left(err) =>
        error(l"loadSelf() failed: $err")
        Future.successful(None)
    }
  }

  override def deleteAccount(): Future[SyncId] = sync.deleteAccount()

  override def getSelfUser: Future[Option[UserData]] =
    usersStorage.get(selfUserId) flatMap {
      case Some(userData) => Future successful Some(userData)
      case _ => syncSelfNow
    }

  /**
   * Schedules user data sync if user with given id doesn't exist or has old timestamp.
  */

  override def syncIfNeeded(userIds: Set[UserId], olderThan: FiniteDuration = SyncIfOlderThan): Future[Option[SyncId]] =
    usersStorage.listAll(userIds).flatMap { found =>
      val newIds = userIds -- found.map(_.id)
      val offset = LocalInstant.Now - olderThan
      val existing = found.filter(u => !u.isConnected && (u.teamId.isEmpty || u.teamId != teamId) && u.syncTimestamp.forall(_.isBefore(offset)))
      val toSync = newIds ++ existing.map(_.id)
      verbose(l"syncIfNeeded for users; new: (${newIds.size}) + existing: (${existing.size}) = all: (${toSync.size})")
      if (toSync.nonEmpty) sync.syncUsers(toSync).map(Some(_))(Threading.Background) else Future.successful(None)
    }

  override def updateSyncedUsers(users: Seq[UserInfo], syncTime: LocalInstant = LocalInstant.Now): Future[Set[UserData]] = {
    verbose(l"update synced ${users.size} users")
    def updateOrCreate(info: UserInfo): Option[UserData] => UserData = {
      case Some(user: UserData) =>
        user.updated(info).copy(syncTimestamp = Some(syncTime), connection = if (selfUserId == info.id) ConnectionStatus.Self else user.connection)
      case None =>
        UserData(info).copy(syncTimestamp = Some(syncTime), connection = if (selfUserId == info.id) ConnectionStatus.Self else ConnectionStatus.Unconnected)
    }

    for {
      _       <- users.find(_.id == selfUserId) match {
                   case Some(info) if info.ssoId.isDefined => accsStorage.update(info.id, _.copy(ssoId = info.ssoId))
                   case _ => Future.successful(Option.empty[(AccountData, AccountData)])
                 }
      updated <- usersStorage.updateOrCreateAll(users.map(info => info.id -> updateOrCreate(info))(breakOut))
    } yield updated
  }

  //TODO - remove and find a better flow for the settings
  override def setEmail(email: EmailAddress, password: Password) =
    credentialsClient.updateEmail(email).future.flatMap {
      case Right(_) => setPassword(password)
      case Left(e) => Future.successful(Left(e))
    }


  override def updateEmail(email: EmailAddress) =
    credentialsClient.updateEmail(email).future

  override def updatePhone(phone: PhoneNumber) =
    credentialsClient.updatePhone(phone).future


  override def clearPhone(): ErrorOr[Unit] =
    for {
      resp <- credentialsClient.clearPhone().future
      _    <- resp.mapFuture(_ => usersStorage.update(selfUserId, _.copy(phone = None)).map(_ => {}))
    } yield resp

  // A hacky solution for now: we pretend to change the password to itself and interpret
  // the error message "password must differ" as the correct one and all others as failures.
  // Note that it's not possible to get `Right` as the response from BE.
  override def checkPassword(password: Password): ErrorOr[Unit] =
    credentialsClient.updatePassword(password, Some(password)).future.map {
      case Left(err) if err.code == PasswordMustDifferCode && err.label == PasswordMustDifferLabel => Right(())
      case otherErr => otherErr
    }

  override def changePassword(newPassword: Password, currentPassword: Password) =
    credentialsClient.updatePassword(newPassword, Some(currentPassword)).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_)  => accsStorage.update(selfUserId, _.copy(password = Some(newPassword))).map(_ => Right({}))
    }

  override def setPassword(password: Password) =
    credentialsClient.updatePassword(password, None).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_)  => accsStorage.update(selfUserId, _.copy(password = Some(password))).map(_ => Right({}))
    }

  override def updateHandle(handle: Handle) =
    credentialsClient.updateHandle(handle).future.flatMap {
      case Right(_) => usersStorage.update(selfUserId, _.copy(handle = Some(handle))).map(_ => Right({}))
      case Left(err) => Future.successful(Left(err))
    }

  override def updateName(name: Name) =
    updateAndSync(_.copy(name = name), _ => sync.postSelfName(name))

  override def updateAccentColor(color: AccentColor) =
    updateAndSync(_.copy(accent = color.id), _ => sync.postSelfUser(UserInfo(selfUserId, accentId = Some(color.id))))

  override def updateAvailability(availability: Availability) =
    updateAndSync(
      _.copy(availability = availability),
      _ => sync.postAvailability(availability)
    )

  override def storeAvailabilities(availabilities: Map[UserId, Availability]) = {
    usersStorage.updateAll2(availabilities.keySet, u => availabilities.get(u.id).fold(u)(av => u.copy(availability = av)))
  }

  override def updateSelfPicture(content: Content) = {
    val contentForUpload = ContentForUpload("profile-picture", content)
    for {
      asset <- assets.createAndSaveUploadAsset(contentForUpload, NoEncryption, public = true, Retention.Eternal, None)
      _     <- updateAndSync(_.copy(picture = Some(PictureNotUploaded(asset.id))), _ => sync.postSelfPicture(asset.id))
    } yield ()
  }

  private def updateAndSync(updater: UserData => UserData, sync: UserData => Future[_]) =
    updateUserData(selfUserId, updater).flatMap({
      case Some((p, u)) if p != u => sync(u).map(_ => {})
      case _ => Future.successful({})
    })
}

object UserService {

  val SyncIfOlderThan = 24.hours

  val UnsplashUrl = AndroidURIUtil.parse("https://source.unsplash.com/800x800/?landscape")

  lazy val AcceptedOrBlocked = Set(ConnectionStatus.Accepted, ConnectionStatus.Blocked)

  val PasswordMustDifferCode = 409
  val PasswordMustDifferLabel = "password-must-differ"
}

/**
  * Whenever the selected conversation changes, this small service checks to see which users of that conversation are a
  * wireless guest user. It then starts a countdown timer for the remaining duration of the life of the user, and at the
  * end of that timer, fires a sync request to trigger a BE check
  */
class ExpiredUsersService(push:         PushService,
                          members:      MembersStorage,
                          users:        UserService,
                          usersStorage: UsersStorage,
                          sync:         SyncServiceHandle)(implicit ev: AccountContext) extends DerivedLogTag {

  private implicit val ec = SerialDispatchQueue(name = "ExpiringUsers")

  private var timers = Map[UserId, CancellableFuture[Unit]]()

  //if a given user is removed from all conversations, drop the timer
  members.onDeleted(_.foreach { m =>
    members.getByUsers(Set(m._1)).map(_.isEmpty).map {
      case true =>
        timers.get(m._1).foreach(_.cancel())
        timers -= m._1
      case _ =>
    }
  })

  (for {
    membersIds <- users.currentConvMembers
    members    <- Signal.sequence(membersIds.map(usersStorage.signal).toSeq: _*)
  } yield members.filter(_.expiresAt.isDefined).toSet){ wireless =>
    push.beDrift.head.map { drift =>
      val woTimer = wireless.filter(u => (wireless.map(_.id) -- timers.keySet).contains(u.id))
      woTimer.foreach { u =>
        val delay = LocalInstant.Now.toRemote(drift).remainingUntil(u.expiresAt.get + 10.seconds)
        timers += u.id -> CancellableFuture.delay(delay).map { _ =>
          sync.syncUsers(Set(u.id))
          timers -= u.id
        }
      }
    }
  }
}
