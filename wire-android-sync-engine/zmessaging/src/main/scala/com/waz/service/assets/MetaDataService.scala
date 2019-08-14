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
package com.waz.service.assets

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever._
import com.waz.bitmap.BitmapUtils
import com.waz.cache.{CacheEntry, CacheService, LocalData}
import com.waz.content.AssetsStorage
import com.waz.log.LogSE._
import com.waz.content.WireContentProvider.CacheUri
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.AssetMetaData.{Audio, Empty}
import com.waz.model._
import com.waz.service.images.ImageAssetGenerator
import com.waz.service.images.ImageAssetGenerator._
import com.waz.service.images.ImageLoader.Metadata
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.Serialized
import org.threeten.bp

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class MetaDataService(context: Context,
                      cache: CacheService,
                      storage: AssetsStorage,
                      assets: => AssetService,
                      generator: ImageAssetGenerator) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def loadMetaData(asset: AssetData, data: LocalData): CancellableFuture[Option[AssetMetaData]] = {
    def load(entry: CacheEntry) = {
      asset.mime match {
        case Mime.Video() => AssetMetaData.Video(entry.cacheFile).map {_.fold({ msg => error(l"${showString(msg)}"); None }, Some(_))}
        case Mime.Audio() => audioMetaData(asset, entry)
        case Mime.Image() => Future {AssetMetaData.Image(entry.cacheFile)}(Threading.IO)
        case _ => Future successful Some(Empty)
      }
    }.recover {
      case ex =>
        warn(l"failed to get metadata for asset: ${asset.id}", ex)
        None
    }
    withCacheEntry(data, load)
  }

  //generates and stores a preview asset for the given asset data
  def loadPreview(asset: AssetData, data: LocalData): CancellableFuture[Option[AssetData]] = {
    def load(entry: CacheEntry) = {
      asset.mime match {
        case Mime.Video() if asset.previewId.isEmpty =>
          MetaDataRetriever(entry.cacheFile)(loadPreview).flatMap(createVideoPreview).flatMap {
            case Some(prev) => storage.mergeOrCreateAsset(prev)
            case _ =>
              verbose(l"Failed to create video preview for asset: ${asset.id}")
              Future.successful(None)
        }
        case _ => //possible plans to add previews for other types (images, pdfs etc?)
          Future successful None
      }
    }.recover {
      case ex =>
        warn(l"failed to get preview for asset: ${asset.id}", ex)
        None
    }

    Serialized(('MetaService, asset.id))(withCacheEntry(data, load))
  }

  private def withCacheEntry[A](data: LocalData, load: CacheEntry => Future[A]): CancellableFuture[A] = {
    data match {
      case entry: CacheEntry if entry.data.encKey.isEmpty => CancellableFuture lift load(entry)
      case _ =>
        warn(l"loading data from stream (encrypted cache, or generic local data) this is slow, please avoid that")
        for {
          entry <- CancellableFuture lift cache.addStream(CacheKey(), data.inputStream, cacheLocation = Some(cache.intCacheDir))(10.minutes)
          res <- CancellableFuture lift load(entry)
        } yield {
          entry.delete()
          res
        }
    }
  }

  private def audioMetaData(asset: AssetData, entry: CacheEntry): Future[Option[Audio]] = {
    lazy val loudness = AudioLevels(context).createAudioOverview(CacheUri(entry.data, context), asset.mime)
      .recover{case _ => warn(l"Failed to generate loudness levels for audio asset: ${asset.id}"); None}.future

    lazy val duration = MetaDataRetriever(entry.cacheFile) { r =>
      val str = r.extractMetadata(METADATA_KEY_DURATION)
      Try(bp.Duration.ofMillis(str.toLong)).toOption
    }.recover{case _ => warn(l"Failed to extract duration for audio asset: ${asset.id}"); None}

    asset.metaData match {
      case Some(meta@AssetMetaData.Audio(_, Some(_))) => Future successful Some(meta) //nothing to do
      case Some(meta@AssetMetaData.Audio(_, _)) => loudness.map { //just generate loudness
        case Some(l) => Some(AssetMetaData.Audio(meta.duration, Some(l)))
        case _ => Some(meta)
      }
      case _ => for { //no metadata - generate everything
        l <- loudness
        d <- duration
      } yield d match {
        case Some(d) => Some(AssetMetaData.Audio(d, l))
        case _ => None
      }
    }
  }

  private def loadPreview(retriever: MediaMetadataRetriever) = Try(Option(retriever.getFrameAtTime(-1L, OPTION_CLOSEST_SYNC))).toOption.flatten

  private def createVideoPreview(bitmap: Option[Bitmap]) = bitmap match {
    case None => CancellableFuture successful None
    case Some(b) => generator.generateAssetData(AssetData.newImageAsset(AssetId(), Medium), Right(b), Metadata(b.getWidth, b.getHeight, BitmapUtils.Mime.Jpg), MediumOptions).map(Some(_))
  }
}
