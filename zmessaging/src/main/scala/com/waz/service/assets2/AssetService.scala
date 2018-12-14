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
package com.waz.service.assets2

import java.io.InputStream
import java.net.URI

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.cache2.CacheService
import com.waz.model.errors._
import com.waz.model.{AssetId, Mime}
import com.waz.service.assets2.Asset.General
import com.waz.sync.client.AssetClient2
import com.waz.sync.client.AssetClient2.{AssetContent, Metadata}
import com.waz.threading.CancellableFuture
import com.waz.znet2.http.HttpClient._
import com.waz.znet2.http.ResponseCode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

trait AssetService {
  def loadContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[InputStream]
  def loadContent(asset: Asset[General], callback: Option[ProgressCallback] = None): CancellableFuture[InputStream]
  def uploadAsset(rawAsset: RawAsset[General], callback: Option[ProgressCallback] = None): CancellableFuture[Asset[General]]
}

object AssetService {

  case class MetaData(mime: Mime, name: Option[String], size: Option[Long])

  trait MetaDataExtractor {
    def extractMetadata(uri: URI): Future[MetaData]
  }

}

class AssetServiceImpl(assetsStorage: AssetStorage,
                       uriHelper: UriHelper,
                       cache: CacheService,
                       assetClient: AssetClient2)
                      (implicit ec: ExecutionContext) extends AssetService {

  protected def cacheKey(asset: Asset[General]): String = asset.id.str

  private def loadFromBackend(asset: Asset[General], callback: Option[ProgressCallback]): CancellableFuture[InputStream] = {
    verbose(s"Load asset content from backend. $asset")
    assetClient.loadAssetContent(asset, callback).flatMap {
      case Left(err) if err.code == ResponseCode.NotFound =>
        cache
          .remove(cacheKey(asset))
          .flatMap(_ => CancellableFuture.failed(NotFoundRemote(s"Asset '$asset'")))
          .toCancellable
      case Left(err) =>
        CancellableFuture.failed(NetworkError(err))
      case Right(fileWithSha) if fileWithSha.sha256 != asset.sha =>
        CancellableFuture.failed(ValidationError(s"SHA256 is not equal. Asset: $asset"))
      case Right(fileWithSha) =>
        cache.putEncrypted(cacheKey(asset), fileWithSha.file)
          .flatMap(_ => cache.get(cacheKey(asset))(asset.encryption))
          .toCancellable
    }
  }

  private def loadFromCache(asset: Asset[General], callback: Option[ProgressCallback]): CancellableFuture[InputStream] = {
    verbose(s"Load asset content from cache. $asset")
    cache.get(cacheKey(asset))(asset.encryption)
      .recoverWith { case err =>
        verbose(s"Can not load asset content from cache. $err")
        Future.failed(err)
      }
      .toCancellable
  }

  private def loadFromFileSystem(asset: Asset[General], callback: Option[ProgressCallback]): CancellableFuture[InputStream] = {
    verbose(s"Load asset content from file system. $asset")
    lazy val emptyUriError = new NoSuchElementException("Asset does not have local source property.")
    Future { asset.localSource.map(uriHelper.openInputStream).getOrElse(Failure(throw emptyUriError)) }
      .flatMap(Future.fromTry)
      .recoverWith { case err =>
        debug(s"Can not load content from file system. $err")
        verbose(s"Clearing local source asset property. $asset")
        assetsStorage.save(asset.copy(localSource = None)).flatMap(_ => Future.failed(err))
      }
      .toCancellable
  }

  override def loadContentById(assetId: AssetId, callback: Option[ProgressCallback] = None): CancellableFuture[InputStream] =
    assetsStorage.get(assetId).flatMap(asset => loadContent(asset, callback)).toCancellable

  override def loadContent(asset: Asset[General], callback: Option[ProgressCallback] = None): CancellableFuture[InputStream] =
    assetsStorage.find(asset.id)
      .flatMap { fromStorage =>
        if (fromStorage.isEmpty)
          assetsStorage.save(asset).flatMap(_ => loadFromBackend(asset, callback))
        else if (asset.localSource.isEmpty)
          loadFromCache(asset, callback).recoverWith { case _ => loadFromBackend(asset, callback) }
        else
          loadFromFileSystem(asset, callback).recoverWith { case _ => loadFromBackend(asset, callback) }
      }
      .toCancellable

  //TODO We should do something with asset source. In some cases we can delete it.
  override def uploadAsset(rawAsset: RawAsset[General], callback: Option[ProgressCallback]): CancellableFuture[Asset[General]] = {
    val content = AssetContent(rawAsset.mime, () => uriHelper.openInputStream(rawAsset.uri).get, Some(rawAsset.size))
    val metadata = Metadata(public = rawAsset.public, retention = rawAsset.retention)

    assetClient.uploadAsset(metadata, content, callback).flatMap {
      case Right(response) =>
        val asset = Asset(AssetId(response.rId.str), response.token, rawAsset)
        assetsStorage.save(asset).map(_ => asset).toCancellable
      case Left(err) =>
        verbose(s"Error while uploading asset: $err")
        CancellableFuture.failed(NetworkError(err))
    }
  }

}

object AssetServiceImpl {}
