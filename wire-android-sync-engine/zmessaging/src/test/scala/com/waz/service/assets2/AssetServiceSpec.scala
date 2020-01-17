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

import java.io._
import java.net.URI

import com.waz.api.impl.ErrorResponse
import com.waz.content.AccountStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.{debug, _}
import com.waz.log.LogShow
import com.waz.model.errors.NotFoundLocal
import com.waz.model.{AccountData, AssetId, Mime, Sha256, UploadAssetId}
import com.waz.service.AccountsService
import com.waz.service.assets._
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.AssetClient.{FileWithSha, Retention}
import com.waz.sync.client.{AssetClient, AssetClientImpl}
import com.waz.threading.CancellableFuture
import com.waz.utils.{IoUtils, ReactiveStorageImpl2, UnlimitedInMemoryStorage, returning}
import com.waz.{AuthenticationConfig, FilesystemUtils, ZIntegrationMockSpec}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

@RunWith(classOf[JUnitRunner])
class AssetServiceSpec extends ZIntegrationMockSpec with DerivedLogTag with AuthenticationConfig {

  private val assetStorage        = mock[AssetStorage]
  private val inProgressAssetStorage   = mock[DownloadAssetStorage]
  private val rawAssetStorage     = mock[UploadAssetStorage]
  private val assetDetailsService = mock[AssetDetailsService]
  private val restrictionsService      = mock[AssetRestrictionsService]
  private val transformationsService      = mock[AssetTransformationsService]
  private val previewService      = mock[AssetPreviewService]
  private val cache               = mock[AssetContentCache]
  private val rawCache            = mock[UploadAssetContentCache]
  private val client              = mock[AssetClient]
  private val uriHelperMock       = mock[UriHelper]
  private val syncHandle          = mock[SyncServiceHandle]

  override val accountsService: AccountsService = mock[AccountsService]
  override val accountStorage: AccountStorage = mock[AccountStorage]

  private val testAssetContent = returning(Array.ofDim[Byte](128))(Random.nextBytes)

  lazy private val testAsset = Asset(
    id = AssetId(),
    token = None,
    sha = Sha256.calculate(testAssetContent),
    mime = Mime.Default,
    encryption = NoEncryption,
    localSource = None,
    preview = None,
    name = "test_content",
    size = testAssetContent.length,
    details = BlobDetails,
    convId = None
  )

  private def service(rawAssetStorage: UploadAssetStorage = rawAssetStorage,
                      client: AssetClient = client): AssetService =
    new AssetServiceImpl(
      assetStorage,
      rawAssetStorage,
      inProgressAssetStorage,
      assetDetailsService,
      previewService,
      transformationsService,
      restrictionsService,
      uriHelperMock,
      cache,
      rawCache,
      client,
      syncHandle
    )

  feature("Assets") {

    scenario("load asset content if it does not exist in cache and asset does not exist in storage") {
      val testDir = FilesystemUtils.createDirectoryForTest()
      val downloadAssetResult = {
        val file = new File(testDir, "asset_content")
        IoUtils.write(new ByteArrayInputStream(testAssetContent), new FileOutputStream(file))
        FileWithSha(file, Sha256.calculate(testAssetContent))
      }

      (assetStorage.find _).expects(*).once().returns(Future.successful(None))
      (assetStorage.save _).expects(testAsset).once().returns(Future.successful(()))
      (client.loadAssetContent _)
        .expects(testAsset, *)
        .once()
        .returns(CancellableFuture.successful(Right(downloadAssetResult)))
      (cache.put _).expects(*, *, *).once().returns(Future.successful(()))
      (cache.getStream _).expects(*).once().returns(Future.successful(new ByteArrayInputStream(testAssetContent)))

      for {
        result <- service().loadContent(testAsset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("load asset content if it does not exist in cache") {
      val testDir = FilesystemUtils.createDirectoryForTest()
      val downloadAssetResult = {
        val file = new File(testDir, "asset_content")
        IoUtils.write(new ByteArrayInputStream(testAssetContent), new FileOutputStream(file))
        FileWithSha(file, Sha256.calculate(testAssetContent))
      }

      (assetStorage.find _).expects(*).once().returns(Future.successful(Some(testAsset)))
      (cache.getStream _).expects(*).once().returns(Future.failed(NotFoundLocal("not found")))
      (client.loadAssetContent _)
        .expects(testAsset, *)
        .once()
        .returns(CancellableFuture.successful(Right(downloadAssetResult)))
      (cache.put _).expects(*, *, *).once().returns(Future.successful(()))
      (cache.getStream _).expects(*).once().returns(Future.successful(new ByteArrayInputStream(testAssetContent)))

      for {
        result <- service().loadContent(testAsset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("load asset content if it exists in cache") {
      (assetStorage.find _).expects(*).once().returns(Future.successful(Some(testAsset)))
      (cache.getStream _).expects(*).once().returns(Future.successful(new ByteArrayInputStream(testAssetContent)))

      for {
        result <- service().loadContent(testAsset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("load asset content if it has not empty local source") {
      val asset =
        testAsset.copy(localSource = Some(LocalSource(new URI("www.test"), Sha256.calculate(testAssetContent))))

      (assetStorage.find _).expects(*).once().returns(Future.successful(Some(asset)))
      (uriHelperMock.openInputStream _)
        .expects(*)
        .once()
        .onCall({ _: URI =>
          Success(new ByteArrayInputStream(testAssetContent))
        })

      for {
        result <- service().loadContent(asset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("load asset content if it has not empty local source and we can not load content") {
      val asset =
        testAsset.copy(localSource = Some(LocalSource(new URI("www.test"), Sha256.calculate(testAssetContent))))
      val testDir = FilesystemUtils.createDirectoryForTest()
      val downloadAssetResult = {
        val file = new File(testDir, "asset_content")
        IoUtils.write(new ByteArrayInputStream(testAssetContent), new FileOutputStream(file))
        FileWithSha(file, Sha256.calculate(testAssetContent))
      }

      (assetStorage.find _).expects(*).once().returns(Future.successful(Some(asset)))
      (uriHelperMock.openInputStream _).expects(*).once().returns(Failure(new IllegalArgumentException))
      (assetStorage.save _).expects(asset.copy(localSource = None)).once().returns(Future.successful(()))
      (client.loadAssetContent _)
        .expects(asset, *)
        .once()
        .returns(CancellableFuture.successful(Right(downloadAssetResult)))
      (cache.put _).expects(*, *, *).once().returns(Future.successful(()))
      (cache.getStream _).expects(*).once().returns(Future.successful(new ByteArrayInputStream(testAssetContent)))

      for {
        result <- service().loadContent(asset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("load asset content if it has not empty local source but local source content has changed") {
      val testContentSha = Sha256.calculate(testAssetContent)
      val asset          = testAsset.copy(localSource = Some(LocalSource(new URI("www.test"), testContentSha)))
      val testDir        = FilesystemUtils.createDirectoryForTest()
      val downloadAssetResult = {
        val file = new File(testDir, "asset_content")
        IoUtils.write(new ByteArrayInputStream(testAssetContent), new FileOutputStream(file))
        FileWithSha(file, testContentSha)
      }

      (assetStorage.find _).expects(*).once().returns(Future.successful(Some(asset)))
      //emulating file changing
      (uriHelperMock.openInputStream _)
        .expects(*)
        .once()
        .returns(Success(new ByteArrayInputStream(testAssetContent :+ 1.toByte)))
      (assetStorage.save _).expects(asset.copy(localSource = None)).once().returns(Future.successful(()))
      (client.loadAssetContent _)
        .expects(asset, *)
        .once()
        .returns(CancellableFuture.successful(Right(downloadAssetResult)))
      (cache.put _).expects(*, *, *).once().returns(Future.successful(()))
      (cache.getStream _).expects(*).once().returns(Future.successful(new ByteArrayInputStream(testAssetContent)))

      for {
        result <- service().loadContent(asset, callback = None)
        bytes = IoUtils.toByteArray(result)
      } yield {
        bytes shouldBe testAssetContent
      }
    }

    scenario("upload asset to backend and download it back. check sha") {

      val encryption = AES_CBC_Encryption.random

      val fakeUri = new URI("https://www.youtube.com")
      val contentForUpload = ContentForUpload("test_uri_content", Content.Uri(fakeUri))

      (uriHelperMock.openInputStream _).expects(*).anyNumberOfTimes().onCall { _: URI =>
        Success(new ByteArrayInputStream(testAssetContent))
      }
      (uriHelperMock.extractMime _).expects(*).anyNumberOfTimes().returns(Success(Mime.Default))
      (uriHelperMock.extractSize _).expects(*).anyNumberOfTimes().returns(Success(testAssetContent.length))
      (uriHelperMock.extractFileName _).expects(*).anyNumberOfTimes().returns(Success("test_file_name"))
      (assetDetailsService.extract _).expects(*).anyNumberOfTimes().returns((BlobDetails, Mime.Default))
      (cache.putStream _).expects(*, *).anyNumberOfTimes().returns(Future.successful(()))
      (assetStorage.save _).expects(*).anyNumberOfTimes().returns(Future.successful(()))
      (rawCache.remove _).expects(*).anyNumberOfTimes().returns(Future.successful(()))
      (transformationsService.getTransformations _).expects(*, *).once().returns(List())
      (restrictionsService.validate _).expects(*).once().returns(Success(()))

      for {
        _ <- Future.successful(())
        client = new AssetClientImpl
        rawAssetStorage = new ReactiveStorageImpl2(new UnlimitedInMemoryStorage[UploadAssetId, UploadAsset]()) with UploadAssetStorage
        assetService = service(rawAssetStorage, client)

        rawAsset <- assetService.createAndSaveUploadAsset(contentForUpload, encryption, public = false, Retention.Persistent, None)
        asset <- assetService.uploadAsset(rawAsset.id)
        assetContent <- client.loadAssetContent(asset, None)

        encryptedContent = IoUtils.toByteArray(rawAsset.encryption.encrypt(new ByteArrayInputStream(testAssetContent), rawAsset.encryptionSalt))
        encryptedSha = Sha256.calculate(new ByteArrayInputStream(encryptedContent)).get
      } yield {

        implicit val AssetResponseShow: LogShow[Either[ErrorResponse, FileWithSha]] = LogShow.create(_.toString)
        implicit val StringShow: LogShow[String] = LogShow.create(_.toString)

        debug(l"Download asset response: $assetContent")
        assetContent shouldBe an[Right[ErrorResponse, FileWithSha]]
        val fileWithSha = assetContent.right.get

        debug(l"Initial content : ${testAssetContent.mkString(",")}")
        debug(l"Expected content: ${encryptedContent.mkString(",")}")

        debug(l"Initial content sha: ${Sha256.calculate(testAssetContent)}")
        debug(l"Expected content sha: $encryptedSha")

        asset.sha shouldBe rawAsset.sha
        fileWithSha.sha256 shouldBe asset.sha
      }

    }

  }

  override def setUpAccountData(accountData: AccountData): Unit = {
    (accountStorage.get _).expects(*).anyNumberOfTimes().returning(Future.successful(Some(accountData)))
  }
}
