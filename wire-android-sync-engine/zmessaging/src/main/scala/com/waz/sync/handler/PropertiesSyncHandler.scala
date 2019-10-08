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
import com.waz.model.TeamId
import com.waz.service.{PropertiesService, PropertyKey}
import com.waz.sync.SyncResult
import com.waz.sync.client.{ErrorOrResponse, PropertiesClient}
import com.waz.znet2.http.HttpClient
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

class PropertiesSyncHandler(prefsClient: PropertiesClient, propertiesService: PropertiesService, teamId: Option[TeamId]) {

  import com.waz.threading.Threading.Implicits.Background

  def postProperty[T: Encoder : Decoder](key: String, value: T): Future[SyncResult] = {
    import HttpClient.AutoDerivation._
    prefsClient.putProperty(key, value).map(SyncResult(_))
  }

  def getProperty[T: Encoder : Decoder](key: String): ErrorOrResponse[Option[T]] = {
    import HttpClient.AutoDerivation._
    prefsClient.getProperty(key)
  }

  def syncProperties(): Future[SyncResult] = {

    def syncProperty[T: Encoder: Decoder](key: PropertyKey, default: Option[T]): Future[SyncResult] =
      getProperty[T](key).future.flatMap {
        case Right(Some(v)) => propertiesService.updateProperty[T](key, v).map(_ => SyncResult.Success)
        case Right(None) => default.fold[Future[SyncResult]](Future.successful(SyncResult.Success))(d => postProperty[T](key, d))
        case Left(e) => Future.successful(SyncResult(e))
      }

    for {
      res <- syncProperty[Int](PropertyKey.ReadReceiptsEnabled, teamId.fold(Some(0))(_ => Some(1)) )
    } yield res

  }
}
