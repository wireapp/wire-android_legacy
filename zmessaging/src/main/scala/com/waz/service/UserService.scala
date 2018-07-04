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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.AccentColor
import com.waz.content._
import com.waz.model.AccountData.Password
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.service.UserService._
import com.waz.service.assets.AssetService
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.assets.AssetService.RawAssetInput.{ByteInput, UriInput}
import com.waz.service.conversation.ConversationsListStateService
import com.waz.service.push.PushService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.sync.client.{CredentialsUpdateClient, ErrorOr, UsersClient}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events._
import com.waz.utils.wrappers.{AndroidURIUtil, URI}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Right


trait UserService {
  def userUpdateEventsStage: Stage.Atomic
  def userDeleteEventsStage: Stage.Atomic

  def selfUser: Signal[UserData]

  def currentConvMembers: Signal[Set[UserId]]

  def getSelfUser: Future[Option[UserData]]
  def getOrCreateUser(id: UserId): Future[UserData]
  def updateUserData(id: UserId, updater: UserData => UserData): Future[Option[(UserData, UserData)]]
  def syncIfNeeded(userIds: Set[UserId], olderThan: FiniteDuration = SyncIfOlderThan): Future[Option[SyncId]]
  def updateConnectionStatus(id: UserId, status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None): Future[Option[UserData]]
  def updateUsers(entries: Seq[UserSearchEntry]): Future[Set[UserData]]
  def acceptedOrBlockedUsers: Signal[Map[UserId, UserData]]

  def updateSyncedUsers(users: Seq[UserInfo], timestamp: LocalInstant = LocalInstant.Now): Future[Set[UserData]]

  def deleteAccount(): Future[SyncId]

  //These self user properties can fail in many ways, so we do not sync them and force the user to respond
  def setEmail(email: EmailAddress, password: Password): ErrorOr[Unit]
  def updateEmail(email: EmailAddress): ErrorOr[Unit]
  def updatePhone(phone: PhoneNumber): ErrorOr[Unit]
  def clearPhone(): ErrorOr[Unit]
  def setPassword(password: Password): ErrorOr[Unit]
  def changePassword(newPassword: Password, oldPassword: Password): ErrorOr[Unit]
  def updateHandle(handle: Handle): ErrorOr[Unit]

  //These self user properties should always succeed given no fatal errors, so we update locally and create sync jobs
  def updateName(name: String): Future[Unit]
  def updateAccentColor(color: AccentColor): Future[Unit]
  def updateAvailability(availability: Availability): Future[Unit]

  def storeAvailabilities(availabilities: Map[UserId, Availability]): Future[Seq[(UserData, UserData)]]
  def updateSelfPicture(input: RawAssetInput): Future[Unit]
  def updateSelfPicture(image: com.waz.api.ImageAsset): Future[Unit]
  def updateSelfPicture(image: Array[Byte]): Future[Unit]
  def updateSelfPicture(image: URI): Future[Unit]

  def addUnsplashPicture(): Future[Unit]
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
                      assetsStorage:     AssetsStorage,
                      credentialsClient: CredentialsUpdateClient,
                      stats:             ConversationsListStateService) extends UserService {

  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  private val shouldSyncUsers = userPrefs.preference(UserPreferences.ShouldSyncUsers)

  for {
    shouldSync <- shouldSyncUsers()
  } if (shouldSync) {
    verbose("Syncing user data to get team ids")
    usersStorage.list()
      .flatMap(users => sync.syncUsers(users.map(_.id).toSet - selfUserId))
      .flatMap(_ => shouldSyncUsers := false)
    }

  val currentConvMembers = for {
    Some(convId) <- stats.selectedConversationId
    membersIds   <- membersStorage.activeMembers(convId)
  } yield membersIds

  currentConvMembers(syncIfNeeded(_))

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
    //TODO handle deleting db and stuff?
    Future.sequence(e.map(event => accounts.logout(event.user))).flatMap { _ =>
      usersStorage.updateAll2(e.map(_.user)(breakOut), _.copy(deleted = true))
    }
  }

  override lazy val acceptedOrBlockedUsers: Signal[Map[UserId, UserData]] =
    new AggregatingSignal[Seq[UserData], Map[UserId, UserData]](
      usersStorage.onChanged, usersStorage.listUsersByConnectionStatus(AcceptedOrBlocked),
      { (accu, us) =>
        val (toAdd, toRemove) = us.partition(u => AcceptedOrBlocked(u.connection))
        accu -- toRemove.map(_.id) ++ toAdd.map(u => u.id -> u)
      }
    )

  override def getOrCreateUser(id: UserId) = usersStorage.getOrElseUpdate(id, {
    sync.syncUsers(Set(id))
    UserData(id, None, DefaultUserName, None, None, connection = ConnectionStatus.Unconnected, searchKey = SearchKey(DefaultUserName), handle = None)
  })

  override def updateConnectionStatus(id: UserId, status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None) =
    usersStorage.update(id, { user => returning(user.updateConnectionStatus(status, time, message))(u => verbose(s"updateConnectionStatus($u)")) }) map {
      case Some((prev, updated)) if prev != updated => Some(updated)
      case _ => None
    }

  override def updateUserData(id: UserId, updater: UserData => UserData) = usersStorage.update(id, updater)

  override def updateUsers(entries: Seq[UserSearchEntry]) = {
    def updateOrAdd(entry: UserSearchEntry) = (_: Option[UserData]).fold(UserData(entry))(_.updated(entry))
    usersStorage.updateOrCreateAll(entries.map(entry => entry.id -> updateOrAdd(entry)).toMap)
  }

  def syncSelfNow: Future[Option[UserData]] = Serialized.future("syncSelfNow", selfUserId) {
    usersClient.loadSelf().future.flatMap {
      case Right(info) =>
        //TODO Dean - remove after v2 transition time
        val v2profilePic = info.mediumPicture.filter(_.convId.isDefined)

        v2profilePic.fold(Future.successful(())){ pic =>
          verbose("User has v2 picture - re-uploading as v3")
          for {
            _ <- sync.postSelfPicture(v2profilePic.map(_.id))
            _ <- assetsStorage.updateAsset(pic.id, _.copy(convId = None)) //mark assets as v3
            _ <- usersClient.updateSelf(info).future
          } yield (())
        }.flatMap (_ => updateSyncedUsers(Seq(info)) map { _.headOption })
      case Left(err) =>
        error(s"loadSelf() failed: $err")
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
      verbose(s"syncIfNeeded for users; new: (${newIds.size}) + existing: (${existing.size}) = all: (${toSync.size})")
      if (toSync.nonEmpty) sync.syncUsers(toSync).map(Some(_))(Threading.Background) else Future.successful(None)
    }

  override def updateSyncedUsers(users: Seq[UserInfo], syncTime: LocalInstant = LocalInstant.Now): Future[Set[UserData]] = {
    verbose(s"update synced ${users.size} users")
    assets.updateAssets(users.flatMap(_.picture.getOrElse(Seq.empty[AssetData]))).flatMap { _ =>
      def updateOrCreate(info: UserInfo): Option[UserData] => UserData = {
        case Some(user: UserData) =>
          user.updated(info).copy(syncTimestamp = Some(syncTime), connection = if (selfUserId == info.id) ConnectionStatus.Self else user.connection)
        case None =>
          UserData(info).copy(syncTimestamp = Some(syncTime), connection = if (selfUserId == info.id) ConnectionStatus.Self else ConnectionStatus.Unconnected)
      }
      usersStorage.updateOrCreateAll(users.map { info => info.id -> updateOrCreate(info) }(breakOut))
    }
  }

  //TODO - remove and find a better flow for the settings
  override def setEmail(email: EmailAddress, password: Password) = {
    verbose(s"setEmail: $email, password: $password")
    credentialsClient.updateEmail(email).future.flatMap {
      case Right(_) => setPassword(password)
      case Left(e) => Future.successful(Left(e))
    }
  }

  override def updateEmail(email: EmailAddress) = {
    verbose(s"updateEmail: $email")
    credentialsClient.updateEmail(email).future
  }

  override def updatePhone(phone: PhoneNumber) = {
    verbose(s"updatePhone: $phone")
    credentialsClient.updatePhone(phone).future
  }

  override def clearPhone(): ErrorOr[Unit] = {
    verbose(s"clearPhone")
    for {
      resp <- credentialsClient.clearPhone().future
      _    <- resp.mapFuture(_ => usersStorage.update(selfUserId, _.copy(phone = None)).map(_ => {}))
    } yield resp
  }

  override def changePassword(newPassword: Password, currentPassword: Password) = {
    verbose(s"changePassword: $newPassword, $currentPassword")
    credentialsClient.updatePassword(newPassword, Some(currentPassword)).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_)  => accsStorage.update(selfUserId, _.copy(password = Some(newPassword))).map(_ => Right({}))
    }
  }

  override def setPassword(password: Password) = {
    verbose(s"setPassword: $password")
    credentialsClient.updatePassword(password, None).future.flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(_)  => accsStorage.update(selfUserId, _.copy(password = Some(password))).map(_ => Right({}))
    }
  }

  override def updateHandle(handle: Handle) = {
    verbose(s"updateHandle: $handle")
    credentialsClient.updateHandle(handle).future.flatMap {
      case Right(_) => usersStorage.update(selfUserId, _.copy(handle = Some(handle))).map(_ => Right({}))
      case Left(err) => Future.successful(Left(err))
    }
  }

  override def updateName(name: String) = {
    verbose(s"updateName: $name")
    updateAndSync(_.copy(name = name), _ => sync.postSelfName(name))
  }

  override def updateAccentColor(color: AccentColor) = {
    verbose(s"updateAccentColor: $color")
    updateAndSync(_.copy(accent = color.id), _ => sync.postSelfUser(UserInfo(selfUserId, accentId = Some(color.id))))
  }

  override def updateAvailability(availability: Availability) = {
    verbose(s"updateAvailability($availability)")
    updateAndSync(_.copy(availability = availability), _ => sync.postAvailability(availability)).map(_ => {})
  }

  override def storeAvailabilities(availabilities: Map[UserId, Availability]) = {
    verbose(s"storeAvailabilities($availabilities)")
    usersStorage.updateAll2(availabilities.keySet, u => availabilities.get(u.id).fold(u)(av => u.copy(availability = av)))
  }

  override def updateSelfPicture(input: RawAssetInput) =
    input match {
      case ByteInput(bytes) => updateSelfPicture(bytes)
      case UriInput(uri)    => updateSelfPicture(uri)
      case _ => //TODO define other cases
        warn(s"Image input type: $input not supported")
        Future.successful({})
    }

  override def updateSelfPicture(image: com.waz.api.ImageAsset) =
    updateAndSyncSelfPicture {
      verbose(s"updateSelfPicture($image)")
      assets.addImageAsset(image, isProfilePic = true)
    }

  override def updateSelfPicture(bytes: Array[Byte]) =
    updateAndSyncSelfPicture {
      verbose(s"updateSelfPicture(byte array of length: ${bytes.length})")
      assets.createImageFrom(bytes, isProfilePic = true)
    }

  override def updateSelfPicture(uri: URI) =
    updateAndSyncSelfPicture {
      verbose(s"updateSelfPicture($uri)")
      assets.createImageFrom(uri, isProfilePic = true)
    }

  private def updateAndSyncSelfPicture(asset: Future[AssetData]) = asset.flatMap { a =>
    updateAndSync(_.copy(picture = Some(a.id)), _ => sync.postSelfPicture(Some(a.id)))
  }

  def addUnsplashPicture() = {
    verbose(s"addUnsplashPicture")
    val asset = AssetData.newImageAssetFromUri(uri = UnsplashUrl)
    assets.addImage(asset, isProfilePic = true) flatMap { asset =>
      updateAndSync(_.copy(picture = Some(asset.id)), _ => sync.postSelfPicture(Some(asset.id)))
    }
  }

  private def updateAndSync(updater: UserData => UserData, sync: UserData => Future[_]) =
    updateUserData(selfUserId, updater).flatMap({
      case Some((p, u)) if p != u => sync(u).map(_ => {})
      case _ => Future.successful({})
    })

}

object UserService {
  val DefaultUserName: String = ""

  val SyncIfOlderThan = 24.hours

  val UnsplashUrl = AndroidURIUtil.parse("https://source.unsplash.com/800x800/?landscape")

  lazy val AcceptedOrBlocked = Set(ConnectionStatus.Accepted, ConnectionStatus.Blocked)
}

/**
  * Whenever the selected conversation changes, this small service checks to see which users of that conversation are a
  * wireless guest user. It then starts a countdown timer for the remaining duration of the life of the user, and at the
  * end of that timer, fires a sync request to trigger a BE check
  */
class ExpiredUsersService(convState:    ConversationsListStateService,
                          push:         PushService,
                          members:      MembersStorage,
                          users:        UserService,
                          usersStorage: UsersStorage,
                          sync:         SyncServiceHandle)(implicit ev: AccountContext) {

  private implicit val ec = new SerialDispatchQueue(name = "ExpiringUsers")

  private var timers = Map[UserId, CancellableFuture[Unit]]()

  //if a given user is removed from all conversations, drop the timer
  members.onDeleted(_.foreach { m =>
    members.getByUsers(Set(m._1)).map(_.isEmpty).map {
      case true =>
        timers.get(m._1).foreach { t =>
          verbose(s"Cancelled timer for user: ${m._1}")
          t.cancel()
        }
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
        verbose(s"Creating timer to remove user: ${u.id}:${u.name} in $delay")
        timers += u.id -> CancellableFuture.delay(delay).map { _ =>
          verbose(s"Wireless user ${u.id}:${u.name} is expired, informing BE")
          sync.syncUsers(Set(u.id))
          timers -= u.id
        }
      }
    }
  }
}
