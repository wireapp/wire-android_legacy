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
package com.waz.service.images

import com.waz.ZLog.ImplicitTag._
import com.waz.api.NetworkMode
import com.waz.model._
import com.waz.service.NetworkModeService
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.downloads.AssetLoader.DownloadOnWifiOnlyException
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.CancellableFuture
import com.waz.ui.MemoryImageCache
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.ui.MemoryImageCache.BitmapRequest.Regular
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.{Bitmap, FakeBitmap}

import scala.concurrent.Future

class BitmapSignalSpec extends AndroidFreeSpec { test =>

  val network = mock[NetworkModeService]
  val imageCache = mock[MemoryImageCache]
  val loader = mock[ImageLoader]
  (loader.memoryCache _).expects().anyNumberOfTimes().returning(imageCache)

  def mockBitmap(asset: AssetData) = FakeBitmap(asset.sizeInBytes.toInt, asset.dimensions.width, asset.dimensions.height, asset.sizeInBytes > 0)

  def image(w: Int, h: Int, mime: Mime = Mime.Image.Png, preview: Option[AssetId] = None) =
    AssetData(mime = mime, metaData = Some(AssetMetaData.Image(Dim2(w, h))), previewId = preview)

  feature("Wire asset loading") {

    def init(asset: AssetData, req: BitmapRequest, preview: Option[AssetData] = None, expectPreview: Boolean = false) = {
      val expectedAsset = preview match {
        case Some(prev) if expectPreview => prev
        case _ => asset
      }

      val bmps = Signal(Option.empty[Bitmap])

      (imageCache.get _).expects(expectedAsset.id, req, expectedAsset.width).anyNumberOfTimes().onCall { _ => bmps.currentValue.flatten }
      (loader.hasCachedBitmap _).expects(expectedAsset, req).anyNumberOfTimes().onCall { (asset, req) =>
        Future.successful(imageCache.get(asset.id, req, asset.width).isDefined)
      }

      (loader.loadBitmap _).expects(expectedAsset, req, *).once().onCall { _ =>
        val bitmap = mockBitmap(expectedAsset)
        bmps ! Some(bitmap)
        CancellableFuture.successful(bitmap)
      }

      (loader.loadCachedBitmap _).expects(expectedAsset, req).anyNumberOfTimes.onCall { _ =>
        bmps.currentValue.flatten match {
          case Some(bitmap) => CancellableFuture.successful(bitmap)
          case None => fail("Trying to return a non-existing cached bitmap")
        }
      }
    }

    def getSignal(asset: AssetData, req: BitmapRequest)(assetSource: (AssetId) => Option[AssetData]) = {
      val signal = new AssetBitmapSignal(asset, req, loader, network, { id => Future.successful(assetSource(id)) }, Signal.const(false), forceDownload = false)
      signal.collect { case BitmapLoaded(b, _) => (b.getWidth, b.getHeight) }
    }

    scenario("Request same size image without a preview") {
      val req = Regular(64)
      val res = (64,64)
      val asset = image(64, 64)

      init(asset, req)

      val s1 = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      result(s1.filter(_ == res).head)

      val s2 = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      // this time from the cache
      result(s2.filter(_ == res).head)
    }

    scenario("Load big size with small source image - no scaling") {
      val req = Regular(500)
      val res = (64,64)
      val asset = image(64, 64)

      init(asset, req)

      val s1 = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      result(s1.filter(_ == res).head)

      val s2 = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      // this time from the cache
      result(s2.filter(_ == res).head)
    }

    scenario("Load image from preview when small image is requested") {
      val preview = image(64, 64)

      val req = Regular(65)
      val res = (64,64)
      val asset = image(512, 512, preview = Some(preview.id))

      init(asset, req, Some(preview), expectPreview = true)

      val signal = getSignal(asset, req) {
        case asset.id => Some(asset)
        case preview.id => Some(preview)
        case _ => None
      }

      // loading preview instead of asset
      result(signal.filter(_ == res).head)
    }

    scenario("Load full image when requested is bigger than preview") {
      val preview = image(32, 32)

      val req = Regular(65)
      val res = (512,512)
      val asset = image(512, 512, preview = Some(preview.id))

      init(asset, req, Some(preview))

      val signal = getSignal(asset, req) {
        case asset.id => Some(asset)
        case preview.id => Some(preview)
        case _ => None
      }

      result(signal.filter(_ == res).head)
    }
  }

  feature("Restart on wifi") {
    val req = Regular(64)
    val res = Some((64,64))
    val asset = image(64, 64)

    val downloadImagesAlways = Signal[Boolean](false)
    val networkMode = Signal(NetworkMode._4G)

    def init(): Unit = {
      (network.networkMode _).expects().anyNumberOfTimes.returning(networkMode)

      (loader.memoryCache _).expects().anyNumberOfTimes.returning(imageCache)
      (loader.hasCachedBitmap _).expects(asset, req).anyNumberOfTimes.returning(Future.successful(false))
      (loader.loadBitmap _).expects(asset, req, *).anyNumberOfTimes.onCall { _ =>
        (networkMode.currentValue, downloadImagesAlways.currentValue) match {
          case (Some(NetworkMode.WIFI), _) => CancellableFuture.successful(mockBitmap(asset))
          case (_, Some(true)) => CancellableFuture.successful(mockBitmap(asset))
          case _ => CancellableFuture.failed(DownloadOnWifiOnlyException)
        }
      }
    }

    def getSignal(asset: AssetData, req: BitmapRequest)(assetSource: (AssetId) => Option[AssetData]) =
      new AssetBitmapSignal(asset, req, loader, network, { id => Future.successful(assetSource(id)) }, downloadImagesAlways, forceDownload = false)
      .map {
        case BitmapLoaded(b, _) => Some((b.getWidth, b.getHeight))
        case _ => None
      }

    scenario("load restart after switching to wifi") {
      init()

      val signal = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      result(networkMode.filter(_ == NetworkMode._4G).head) // waiting for the signals to settle down
      result(downloadImagesAlways.filter(_ == false).head)
      result(signal.filter(_.isEmpty).head)

      networkMode ! NetworkMode.WIFI // switching to wifi should trigger reloading
      result(networkMode.filter(_ == NetworkMode.WIFI).head)
      result(signal.filter(_ == res).head)

      awaitAllTasks
    }

    scenario("load restart after switching to download always") {
      init()

      val signal = getSignal(asset, req){
        case asset.id => Some(asset)
        case _ => None
      }

      result(networkMode.filter(_ == NetworkMode._4G).head) // waiting for the signals to settle down
      result(downloadImagesAlways.filter(_ == false).head)
      result(signal.filter(_.isEmpty).head)

      downloadImagesAlways ! true
      result(downloadImagesAlways.filter(_ == true).head)
      result(signal.filter(_ == res).head)

      awaitAllTasks
    }
  }
}
