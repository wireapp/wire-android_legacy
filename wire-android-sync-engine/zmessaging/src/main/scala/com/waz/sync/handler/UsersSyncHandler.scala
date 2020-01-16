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

import com.waz.log.LogSE._
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.UserInfo.ProfilePicture
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.UserService
import com.waz.service.assets.AssetService
import com.waz.sync.SyncResult
import com.waz.sync.client.UsersClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future

class UsersSyncHandler(userService: UserService,
                       usersStorage: UsersStorage,
                       assets: AssetService,
                       usersClient: UsersClient,
                       otrSync: OtrSyncHandler) extends DerivedLogTag {

  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  def syncUsers(ids: UserId*): Future[SyncResult] =
    usersClient.loadUsers(ids).future flatMap {
      case Right(users) =>
        userService
          .updateSyncedUsers(users)
          .map(_ => SyncResult.Success)
      case Left(error) =>
        Future.successful(SyncResult(error))
    }

  def syncSelfUser(): Future[SyncResult] = usersClient.loadSelf().future flatMap {
    case Right(user) =>
      userService
        .updateSyncedUsers(IndexedSeq(user))
        .map(_ => SyncResult.Success)
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  def postSelfName(name: Name): Future[SyncResult] = usersClient.loadSelf().future flatMap {
    case Right(user) =>
      updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(user.id, name = Some(name))))
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  def postSelfAccentColor(color: AccentColor): Future[SyncResult] = usersClient.loadSelf().future flatMap {
    case Right(user) =>
      updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(user.id, accentId = Some(color.id))))
    case Left(error) =>
      Future.successful(SyncResult(error))
  }

  def postSelfUser(info: UserInfo): Future[SyncResult] =
    updatedSelfToSyncResult(usersClient.updateSelf(info))

  def postSelfPicture(assetId: UploadAssetId): Future[SyncResult] =
    userService.getSelfUser flatMap {
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
      case _ => Future.successful(SyncResult.Retry())
    }

  def postAvailability(availability: Availability): Future[SyncResult] = {
    verbose(l"postAvailability($availability)")
    otrSync.broadcastMessage(GenericMessage(Uid(), GenericContent.AvailabilityStatus(availability)))
      .map(SyncResult(_))
  }

  def deleteAccount(): Future[SyncResult] =
    usersClient.deleteAccount().map(SyncResult(_))

  private def updatedSelfToSyncResult(updatedSelf: Future[Either[ErrorResponse, Unit]]): Future[SyncResult] =
    updatedSelf.map(SyncResult(_))
}
