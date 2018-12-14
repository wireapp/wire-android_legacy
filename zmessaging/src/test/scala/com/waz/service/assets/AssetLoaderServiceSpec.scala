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

import com.waz.api.NetworkMode
import com.waz.api.ProgressIndicator.State
import com.waz.api.ProgressIndicator.State._
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.api.impl.{ErrorResponse, ProgressIndicator}
import com.waz.cache.{CacheEntry, CacheEntryData, CacheService}
import com.waz.model.AssetMetaData.Image
import com.waz.model._
import com.waz.service.NetworkModeService
import com.waz.service.downloads.AssetLoader.DownloadFailedException
import com.waz.service.downloads.AssetLoaderService.{MaxConcurrentLoadRequests, MaxRetriesErrorMsg}
import com.waz.service.downloads.{AssetLoader, AssetLoaderService}
import com.waz.specs.AndroidFreeSpec
import com.waz.ZLog.ImplicitTag._
import com.waz.testutils.TestBackoff
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.znet2.http.ResponseCode

import scala.collection.mutable
import scala.concurrent.Promise
import scala.concurrent.duration._

class AssetLoaderServiceSpec extends AndroidFreeSpec {

  val network      = mock[NetworkModeService]
  val cacheService = mock[CacheService]
  implicit val loader = mock[AssetLoader]

  val networkMode  = Signal[NetworkMode]()

  AssetLoaderService.backoff = TestBackoff()

  override protected def beforeEach() = {
    super.beforeEach()
    networkMode ! NetworkMode.WIFI
  }

  override protected def afterEach() = {
    super.afterEach()
    awaitAllTasks
  }

  feature("Simultaneous requests") {

    scenario("Simultaneous load requests for same asset should only perform load once") {

      val asset = getWireAsset()
      val savedEntry = cacheEntry(asset.cacheKey, Uid())

      val finished = Signal[CacheEntry]()
      val loadActive = Signal(false)
      (loader.loadAsset _).expects(asset, *, *).returning {
        loadActive ! true
        CancellableFuture.lift(finished.head)
      }
      val service = getService

      val f1 = service.load(asset)
      val f2 = service.load(asset)

      finished.publish(savedEntry, Threading.Background)
      result(f1) shouldEqual Some(savedEntry)
      result(f2) shouldEqual Some(savedEntry)
    }


    scenario("Same load request a second time while the first is already active should not perform load again") {
      val asset = getWireAsset()
      val savedEntry = cacheEntry(asset.cacheKey, Uid())

      val finished = Signal[CacheEntry]()
      (loader.loadAsset _).expects(asset, *, *).returning(CancellableFuture.lift(finished.head))
      val service = getService

      val f1 = service.load(asset)
      clock + 100.millis
      awaitAllTasks // wait for first load to be active
      val f2 = service.load(asset)

      finished.publish(savedEntry, Threading.Background)
      result(f1) shouldEqual Some(savedEntry)
      result(f2) shouldEqual Some(savedEntry)
    }
  }

  feature("Download retries") {
    scenario("download successful on first attempt") {
      val asset = getWireAsset()
      val savedEntry = cacheEntry(asset.cacheKey, Uid())
      (loader.loadAsset _).expects(asset, *, *).returning(CancellableFuture.successful(savedEntry))
      result(getService.loadRevealAttempts(asset)) shouldEqual (Some(savedEntry), 1)
    }

    scenario("download after multiple retries") {
      val asset = getWireAsset()
      val savedEntry = cacheEntry(asset.cacheKey, Uid())

      var attempts = 0

      (loader.loadAsset _).expects(asset, *, *).anyNumberOfTimes().onCall { (data, callback, force) =>
        attempts += 1
        attempts match {
          case 1 | 2 => CancellableFuture.failed(DownloadFailedException(ErrorResponse(ErrorResponse.TimeoutCode, "", "")))
          case 3 => CancellableFuture.successful(savedEntry)
          case _ => fail("Unexpected number of call attempts")
        }
      }

      result(getService.loadRevealAttempts(asset)(loader)) shouldEqual (Some(savedEntry), 3)
    }

    scenario("give up after max retries") {
      val asset = getWireAsset()
      (loader.loadAsset _).expects(asset, *, *).anyNumberOfTimes.returning(CancellableFuture.failed(DownloadFailedException(ErrorResponse(ErrorResponse.TimeoutCode, "", ""))))
      assert(intercept[Exception](result(getService.loadRevealAttempts(asset)(loader))).getMessage == MaxRetriesErrorMsg)
    }
  }

  feature("Failures") {
    scenario("Unrecoverable failure should abort download") {
      val asset = getWireAsset()
      (loader.loadAsset _).expects(asset, *, *).anyNumberOfTimes.returning(CancellableFuture.failed(DownloadFailedException(ErrorResponse(ResponseCode.Forbidden, "", ""))))
      assert(!intercept[DownloadFailedException](result(getService.loadRevealAttempts(asset)(loader))).isRecoverable)
    }
  }

  feature("Cancelling") {
    scenario("Cancelling active download does not discard work") {
      val service = getService
      val asset = getWireAsset()
      val entry = Signal[CacheEntry]()
      (loader.loadAsset _).expects(asset, *, *).once().onCall { (_, _, _) => CancellableFuture.lift(entry.head)}

      val cf1 = service.load(asset)
      val cf2 = service.load(asset)

      cf1.cancel()("test")

      val res = cacheEntry(asset.cacheKey, Uid())
      entry.publish(res, Threading.Background)

      intercept[CancelException](result(cf1))
      result(cf2) shouldEqual Some(res)
    }

    scenario("Cancelling non-active download does not start work") {

      val service = getService
      //stuff the queue with the max number of concurrent downloads
      val calls = Signal[Int](0)
      val finishedDownload = Signal[AssetId]()
      (loader.loadAsset _).expects(*, *, *).repeat(MaxConcurrentLoadRequests to MaxConcurrentLoadRequests + 1).onCall { (asset, _, _) =>
        calls.mutate(_ + 1)
        val future = finishedDownload.filter(_ == asset.id).map(_ => cacheEntry(asset.cacheKey, Uid())).head
        CancellableFuture.lift(future)
      }
      (1 to MaxConcurrentLoadRequests).map(getWireAsset).map(a => service.load(a))

      //wait for queue to start executing all jobs
      result(calls.filter(_ == MaxConcurrentLoadRequests).head)

      //add another asset and cancel it
      val asset = getWireAsset(MaxConcurrentLoadRequests + 1)
      val cf1 = service.load(asset)
      cf1.cancel()("test")
      intercept[CancelException](result(cf1))

      //finish all active jobs
      (1 to MaxConcurrentLoadRequests).map(_.toString).map(AssetId).foreach(finishedDownload ! _)

      awaitAllTasks

      val asset2 = getWireAsset(MaxConcurrentLoadRequests + 2)
      val cf2 = service.load(asset2)
      finishedDownload ! asset2.id
      result(cf2)
    }

    scenario("Cancelling non-active task through chain of cancellable futures") {
      val service = getService
      //stuff the queue with the max number of concurrent downloads
      val calls = Signal[Int](0)
      val finishedDownload = Signal[AssetId]()
      (loader.loadAsset _).expects(*, *, *).repeat(MaxConcurrentLoadRequests to MaxConcurrentLoadRequests).onCall { (asset, _, _) =>
        calls.mutate(_ + 1)
        val future = finishedDownload.filter(_ == asset.id).map(_ => cacheEntry(asset.cacheKey, Uid())).head
        CancellableFuture.lift(future)
      }
      (1 to MaxConcurrentLoadRequests).map(getWireAsset).map(a => service.load(a))

      //wait for queue to start executing all jobs
      result(calls.filter(_ == MaxConcurrentLoadRequests).head)

      //add another asset and cancel it
      val asset = getWireAsset(MaxConcurrentLoadRequests + 1)


      val cf1 = CancellableFuture({})(Threading.Background).flatMap(_ => service.load(asset))(Threading.Background)
      cf1.cancel()("test")

      intercept[CancelException](result(cf1))

      //finish all active jobs
      (1 to MaxConcurrentLoadRequests).map(_.toString).map(AssetId).foreach(finishedDownload ! _)

      awaitAllTasks
    }

    scenario("Repeating cancelling and loading") {
      val service = getService

      var firstFreeId = 1
      val assets = mutable.ListBuffer[AssetData]()
      def nextAsset(): AssetData = {
        val asset = getWireAsset(firstFreeId)
        firstFreeId += 1
        assets += asset
        asset
      }

      //stuff the queue with the max number of concurrent downloads
      val calls = Signal[Int](0)
      val finishedDownload = Signal[AssetId]()
      (loader.loadAsset _).expects(*, *, *).repeat(MaxConcurrentLoadRequests to MaxConcurrentLoadRequests).onCall { (asset, _, _) =>
        calls.mutate(_ + 1)
        val future = finishedDownload.filter(_ == asset.id).map(_ => cacheEntry(asset.cacheKey, Uid())).head
        CancellableFuture.lift(future)
      }

      def removeFirst() = assets.remove(0)
      def remove(assetId: AssetId) = assets --= assets.filter(_.id == assetId)
      def load(number: Int) = (1 to number).foreach(_ => service.load(nextAsset()))
      def finish(number: Int) = (1 to MaxConcurrentLoadRequests).map(_.toString).map(AssetId).foreach { id =>
        finishedDownload ! _
        remove(id)
      }
      def addAndCancel() = {
        val asset = nextAsset()
        val cf1 = CancellableFuture({})(Threading.Background).flatMap(_ => service.load(asset))(Threading.Background)
        cf1.cancel()("test")
        intercept[CancelException](result(cf1))
        remove(asset.id)
      }

      load(MaxConcurrentLoadRequests - 1)

      //wait for queue to start executing all jobs
      result(calls.filter(_ == MaxConcurrentLoadRequests - 1).head)

      //add another asset and cancel it
      addAndCancel()

      //add another asset and cancel it
      addAndCancel()

      load(1)

      //add another asset and cancel it
      addAndCancel()

      load(1)

      //finish all active jobs
      finish(1)

      load(1)
      addAndCancel()

      finish(assets.size)

      awaitAllTasks
    }
  }

  feature("Throttling") {

    scenario("Execute only MaxConcurrentLoadRequests number of downloads concurrently") {
      val service = getService
      val assets = (1 to MaxConcurrentLoadRequests + 1).map(getWireAsset)

      val calls = Signal[Int](0)
      (loader.loadAsset _).expects(*, *, *).anyNumberOfTimes.onCall { (asset, _, _) =>
        calls.mutate(_ + 1)
        new CancellableFuture(Promise[CacheEntry]())
      }

      assets.map { a =>
        clock + 50.millis
        service.load(a)
      }

      awaitAllTasks
      result(calls.filter(_ == 4).head)
    }
  }

  feature("Download progress") {

    scenario("Report progress for ongoing download") {

      val service = getService
      val asset = getWireAsset()
      val entry = cacheEntry(asset.cacheKey, Uid())

      val finished = Signal[Boolean]()
      var callback = Option.empty[ProgressIndicator.Callback]
      (loader.loadAsset _).expects(asset, *, *).once().onCall { (_, cb, _) =>
        callback = Some(cb)
        CancellableFuture.lift(finished.filter(_ == true).map(_ => entry).head)
      }

      val f = service.load(asset)
      val reportedProgress = service.getLoadProgress(asset.id)

      //wait for job to be started
      result(reportedProgress.filter(_ == ProgressData(0, 0, RUNNING)).head)

      val progress1 = ProgressData(33, 100, RUNNING)
      callback.foreach(_.apply(progress1))
      result(reportedProgress.filter(_ == progress1).head)

      val progress2 = ProgressData(67, 100, RUNNING)
      callback.foreach(_.apply(progress2))
      result(reportedProgress.filter(_ == progress2).head)

      finished ! true
      result(reportedProgress.filter(_ == ProgressData(0, 0, State.COMPLETED)).head)
    }
  }

  def getService   = new AssetLoaderService()

  def getWireAsset(id: Int = 1) =  AssetData(
    id        = AssetId(id.toString),
    mime      = Mime.Image.Jpg,
    remoteId  = Some(RAssetId("rAssetId")),
    token     = Some(AssetToken("token")),
    otrKey    = Some(AESKey("aeskey")),
    sha       = Some(Sha256("sha256")),
    metaData  = Some(Image(Dim2(1080,720), Image.Tag.Empty))
  )

  def cacheEntry(key: CacheKey, fileId: Uid) = new CacheEntry(new CacheEntryData(key), cacheService)

}
