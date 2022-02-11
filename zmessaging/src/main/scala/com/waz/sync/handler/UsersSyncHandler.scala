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
package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.UserInfo.ProfilePicture
import com.waz.model._
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.assets.AssetService
import com.waz.service.{UserSearchService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.client.UsersClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.Threading
import com.waz.zms.BuildConfig

import scala.concurrent.Future

trait UsersSyncHandler {
  def syncUsers(ids: UserId*): Future[SyncResult]
  def syncQualifiedUsers(qIds: Set[QualifiedId]): Future[SyncResult]
  def syncSearchResults(ids: UserId*): Future[SyncResult]
  def syncQualifiedSearchResults(qIds: Set[QualifiedId]): Future[SyncResult]
  def syncSelfUser(): Future[SyncResult]
  def postSelfName(name: Name): Future[SyncResult]
  def postSelfAccentColor(color: AccentColor): Future[SyncResult]
  def postSelfUser(info: UserInfo): Future[SyncResult]
  def postSelfPicture(assetId: UploadAssetId): Future[SyncResult]
  def postAvailability(availability: Availability, limit: Int = UsersSyncHandler.AvailabilityBroadcastLimit): Future[SyncResult]
  def deleteAccount(): Future[SyncResult]
}

final class UsersSyncHandlerImpl(federation:       FederationSupport,
                                 userService:      UserService,
                                 usersStorage:     UsersStorage,
                                 assets:           AssetService,
                                 searchService:    UserSearchService,
                                 usersClient:      UsersClient,
                                 otrSync:          OtrSyncHandler,
                                 teamId:           Option[TeamId],
                                 teamsSyncHandler: TeamsSyncHandler)
  extends UsersSyncHandler with DerivedLogTag {
  import UsersSyncHandler._
  import Threading.Implicits.Background

  private def syncUsers(response: Either[ErrorResponse, Seq[UserInfo]]) = response match {
    case Right(users) =>
      userService.updateSyncedUsers(users).map(_ => SyncResult.Success)
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  override def syncUsers(ids: UserId*): Future[SyncResult] =
    usersClient.loadUsers(ids).future.flatMap(syncUsers)

  override def syncQualifiedUsers(qIds: Set[QualifiedId]): Future[SyncResult] =
    if (federation.isSupported) {
      usersClient.loadQualifiedUsers(qIds).future.flatMap(syncUsers)
    } else {
      val ids: Array[UserId] = qIds.map(_.id).toArray
      syncUsers(ids: _*)
    }

  private def syncSearchResults(response: Either[ErrorResponse, Seq[UserInfo]]) = response match {
    case Right(users) if teamId.isEmpty =>
      searchService.updateSearchResults(users.map(u => u.id -> (u, None)).toMap)
      Future.successful(SyncResult.Success)
    case Right(users) =>
      teamsSyncHandler.getMembers(users.filter(_.teamId == teamId).map(_.id)).map { members =>
        searchService.updateSearchResults(users.map(u => u.id -> (u, members.find(_.user == u.id))).toMap)
        SyncResult.Success
      }
    case Left(error)  =>
      Future.successful(SyncResult(error))
  }

  override def syncSearchResults(ids: UserId*): Future[SyncResult] =
    usersClient.loadUsers(ids).future.flatMap(syncSearchResults)

  override def syncQualifiedSearchResults(qIds: Set[QualifiedId]): Future[SyncResult] =
    if (federation.isSupported) {
      usersClient.loadQualifiedUsers(qIds).future.flatMap(syncSearchResults)
    } else {
      val ids: Array[UserId] = qIds.map(_.id).toArray
      syncSearchResults(ids: _*)
    }

  def syncSelfUser(): Future[SyncResult] = usersClient.loadSelf().future flatMap {
    case Right(user) =>
      userService.updateSyncedUsers(IndexedSeq(user)).map(_ => SyncResult.Success)
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  override def postSelfName(name: Name): Future[SyncResult] = usersClient.loadSelf().future.flatMap {
    case Right(user) =>
      updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(user.id, name = Some(name))))
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  override def postSelfAccentColor(color: AccentColor): Future[SyncResult] = usersClient.loadSelf().future.flatMap {
    case Right(user) =>
      updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(user.id, accentId = Some(color.id))))
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  override def postSelfUser(info: UserInfo): Future[SyncResult] =
    updatedSelfToSyncResult(usersClient.updateSelf(info))

  override def postSelfPicture(assetId: UploadAssetId): Future[SyncResult] = userService.getSelfUser.flatMap {
    case Some(userData) =>
      verbose(l"postSelfPicture($assetId)")
      for {
        uploadedPicId <- assets.uploadAsset(assetId).map(r => r.id).future
        updateInfo    =  UserInfo(userData.id,
                                  picture = Some(Seq(ProfilePicture(uploadedPicId, Tag.Medium), ProfilePicture(uploadedPicId, Tag.Preview)))
                                 )
        _             <- usersStorage.update(userData.id, _.updated(updateInfo))
        _             <- usersClient.updateSelf(updateInfo)
      } yield SyncResult.Success
    case _ =>
      Future.successful(SyncResult.Retry())
  }

  override def postAvailability(availability: Availability, limit: Int = AvailabilityBroadcastLimit): Future[SyncResult] = {
    verbose(l"postAvailability($availability)")
    val gm = GenericMessage(Uid(), GenericContent.AvailabilityStatus(availability))
    for {
      Some(self)     <- userService.getSelfUser
      users          <- usersStorage.values
      localUsers     =  users.filterNot(userService.isFederated) // for now the status is sent only to users on the same backend
      (team, others) =  localUsers.filterNot(u => u.deleted || u.isWireBot).partition(_.isInTeam(self.teamId, self.domain))
      recipients     =  (List(self.id) ++
                         team.filter(_.id != self.id).map(_.id).toList.sorted ++
                         others.filter(_.isConnected).map(_.id).toList.sorted
                        ).take(limit).toSet
      result         <- otrSync.broadcastMessage(gm, recipients = Some(recipients))
    } yield SyncResult(result)
  }

  override def deleteAccount(): Future[SyncResult] =
    usersClient.deleteAccount().map(SyncResult(_))

  private def updatedSelfToSyncResult(updatedSelf: Future[Either[ErrorResponse, Unit]]): Future[SyncResult] =
    updatedSelf.map(SyncResult(_))
}

object UsersSyncHandler {
  val AvailabilityBroadcastLimit = 500
}
