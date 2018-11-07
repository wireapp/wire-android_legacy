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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.model._
import com.waz.service.UserService
import com.waz.service.assets.AssetService
import com.waz.service.images.ImageAssetGenerator
import com.waz.sync.SyncResult
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.client.UsersClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future

class UsersSyncHandler(assetSync: AssetSyncHandler,
                       userService: UserService,
                       usersStorage: UsersStorage,
                       assets: AssetService,
                       usersClient: UsersClient,
                       imageGenerator: ImageAssetGenerator,
                       otrSync: OtrSyncHandler) {
  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  def syncUsers(ids: UserId*): Future[SyncResult] = usersClient.loadUsers(ids).future flatMap {
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

  def postSelfPicture(): Future[SyncResult] =
    userService.getSelfUser flatMap {
      case Some(userData) => userData.picture match {
        case Some(assetId) => postSelfPicture(userData.id, assetId)
        case None          => updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(userData.id, picture = None)))
      }
      case _ => Future.successful(SyncResult.Retry())
    }

  def postAvailability(availability: Availability): Future[SyncResult] = {
    verbose(s"postAvailability($availability)")
    otrSync.broadcastMessage(GenericMessage(Uid(), GenericContent.AvailabilityStatus(availability)))
      .map(SyncResult(_))
  }

  private def postSelfPicture(id: UserId, assetId: AssetId): Future[SyncResult] = for {
    Some(asset) <- assets.getAssetData(assetId)
    preview     <- imageGenerator.generateSmallProfile(asset).future
    _           <- assets.mergeOrCreateAsset(preview) //needs to be in storage for other steps to find it
    res         <- assetSync.uploadAssetData(preview.id, public = true, retention = Retention.Eternal).future flatMap {
      case Right(uploadedPreview) =>
        assetSync.uploadAssetData(assetId, public = true, retention = Retention.Eternal).future flatMap {
          case Right(uploaded) => for {
            _     <- assets.getAssetData(assetId)
            res   <- updatedSelfToSyncResult(usersClient.updateSelf(UserInfo(id, picture = Some(Seq(uploadedPreview, uploaded)))))
          } yield res

          case Left(err) =>
            Future.successful(SyncResult(err))
        }
      case Left(err) =>
        Future.successful(SyncResult(err))
    }
  } yield res

  def deleteAccount(): Future[SyncResult] =
    usersClient.deleteAccount().map(SyncResult(_))

  private def updatedSelfToSyncResult(updatedSelf: Future[Either[ErrorResponse, Unit]]): Future[SyncResult] =
    updatedSelf.map(SyncResult(_))
}
