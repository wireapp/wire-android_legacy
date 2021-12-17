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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.AssetData.AssetDataDao
import com.waz.model.AssetStatus.UploadDone
import com.waz.model._
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorageImpl, TrimmingLruCache, _}

import scala.concurrent.Future

trait AssetsStorage extends CachedStorage[AssetId, AssetData] {
  def updateAsset(id: AssetId, updater: AssetData => AssetData): Future[Option[AssetData]]
  def findByRemoteIds(ids: Iterable[RAssetId]): Future[Set[AssetData]]

  def mergeOrCreateAsset(newData: AssetData): Future[Option[AssetData]]
  def mergeOrCreateAsset(newData: Option[AssetData]): Future[Option[AssetData]]
}

final class AssetsStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[AssetId, AssetData](
    new TrimmingLruCache(context, Fixed(100)), storage)(AssetDataDao, LogTag("AssetsStorage")
  ) with AssetsStorage {

  import com.waz.threading.Threading.Implicits.Background

  //allows overwriting of asset data
  override def updateAsset(id: AssetId, updater: AssetData => AssetData): Future[Option[AssetData]] = update(id, updater).mapOpt {
    case (_, updated) => Some(updated)
  }

  override def findByRemoteIds(ids: Iterable[RAssetId]) =
    storage.read { db =>
      val builder = Set.newBuilder[AssetData]
      AssetDataDao.foreach(asset => asset.remoteId.filter(ids.toSet.contains).foreach(_ => builder += asset))(db)
      builder.result()
    }

  override def mergeOrCreateAsset(newData: AssetData) = mergeOrCreateAsset(Some(newData))
  override def mergeOrCreateAsset(newData: Option[AssetData]) = newData.map(nd => updateOrCreate(nd.id, cur => merge(cur, nd), nd).map(Some(_))).getOrElse(Future.successful(None))

  //Useful for receiving parts of an asset message or remote data. Note, this only merges non-defined properties, any current data remaining as is.
  private def merge(cur: AssetData, newData: AssetData): AssetData = {

    val res = cur.copy(
      mime        = if (cur.mime == Mime.Unknown)  newData.mime         else cur.mime,
      sizeInBytes = if (cur.sizeInBytes == 0)      newData.sizeInBytes  else cur.sizeInBytes,
      remoteId    = if (cur.remoteId.isEmpty)      newData.remoteId     else cur.remoteId,
      token       = if (cur.token.isEmpty)         newData.token        else cur.token,
      otrKey      = if (cur.otrKey.isEmpty)        newData.otrKey       else cur.otrKey,
      sha         = if (cur.sha.isEmpty)           newData.sha          else cur.sha,
      name        = if (cur.name.isEmpty)          newData.name         else cur.name,
      previewId   = if (cur.previewId.isEmpty)     newData.previewId    else cur.previewId,
      metaData    = if (cur.metaData.isEmpty)      newData.metaData     else cur.metaData,
      proxyPath   = if (cur.proxyPath.isEmpty)     newData.proxyPath    else cur.proxyPath,
      source      = if (cur.source.isEmpty)        newData.source       else cur.source,
      data        = if (cur.data.isEmpty)          newData.data         else cur.data
      //TODO Dean: giphy source and caption
    )
    //After merging the two asset data objects, update the resulting status if we now have remote data
    res.copy(status = res.remoteData.fold(res.status)(_ => UploadDone))
  }

}
