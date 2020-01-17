/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.assets

import java.io.File

import android.Manifest
import android.support.test.InstrumentationRegistry
import android.support.test.InstrumentationRegistry.getContext
import android.support.test.filters.MediumTest
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import com.waz.model.Mime
import com.waz.service.assets2
import com.waz.service.assets.AssetStorageImpl.AssetDao
import com.waz.service.assets2._
import com.waz.sync.client.AssetClient
import com.waz.sync.client.AssetClient.Retention
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.utils.wrappers.DB
import com.waz.zclient.TestUtils.{asyncTest, getResourceUri}
import com.waz.zclient.dev.test.R
import com.waz.zclient.storage.GeneralStorageTest.TestSingleDaoDb
import org.junit.runner.RunWith
import org.junit.{After, Before, Rule, Test}
import org.mockito.Mockito
import com.waz.model.errors._
import com.waz.service.assets.{AssetServiceImpl, AssetStorage, AssetStorageImpl, Content, ContentForUpload, DownloadAssetStorage, DownloadAssetStorageImpl, NoEncryption, UploadAssetStorage, UploadAssetStorageImpl, UriHelper}
import com.waz.sync.SyncServiceHandle

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Success

@RunWith(classOf[AndroidJUnit4])
@MediumTest
class AssetServiceTest {

  val assetsClient: AssetClient = Mockito.mock(classOf[AssetClient], "assetsClient")
  val uriHelper: UriHelper = Mockito.mock(classOf[AndroidUriHelper], "uriHelper")
  val syncHandler: SyncServiceHandle = Mockito.mock(classOf[SyncServiceHandle], "syncHandler")

  val lruCacheDirectory = new File(getContext.getCacheDir, s"assets_${System.currentTimeMillis()}")
  val rawCacheDirectory = new File(getContext.getCacheDir, s"raw_assets_${System.currentTimeMillis()}")

  val uriHelperImpl = new AndroidUriHelper(getContext)
  val detailsService = new AssetDetailsServiceImpl(uriHelperImpl)(getContext, global)
  val contentCache = new AssetContentCacheImpl(cacheDirectory = lruCacheDirectory, directorySizeThreshold = 1024 * 1024 * 200L, sizeCheckingInterval = 30.seconds)(Threading.BlockingIO, EventContext.Global)
  val uploadContentCache = new UploadAssetContentCacheImpl(rawCacheDirectory)(Threading.IO)
  val transformationsService = new AssetTransformationsServiceImpl(List(new ImageDownscalingCompressing(new AndroidImageRecoder)))
  val restrictionsService = new AssetRestrictionsServiceImpl(uriHelperImpl, None)
  val previewService = new AssetPreviewServiceImpl()(getContext, global)

  lazy val assetService = new AssetServiceImpl(
    assetStorage,
    uploadAssetStorage,
    downloadAssetStorage,
    detailsService,
    previewService,
    transformationsService,
    restrictionsService,
    uriHelper,
    contentCache,
    uploadContentCache,
    assetsClient,
    syncHandler
  )(global)

  val DatabaseName = s"test_db_${System.currentTimeMillis()}"
  var testDB: DB = _

  var assetStorage: AssetStorage = _
  var uploadAssetStorage: UploadAssetStorage = _
  var downloadAssetStorage: DownloadAssetStorage = _

  @Rule
  def permissions: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

  @Before
  def initializeDB(): Unit = {
    val context = InstrumentationRegistry.getTargetContext
    testDB = new TestSingleDaoDb(context, DatabaseName, AssetDao).getWritableDatabase

    downloadAssetStorage = new DownloadAssetStorageImpl(context, testDB)(Threading.IO)
    uploadAssetStorage = new UploadAssetStorageImpl(context, testDB)(Threading.IO)
    assetStorage = new AssetStorageImpl(context, testDB, Threading.IO)
  }

  @After
  def removeDb(): Unit = {
    val context = InstrumentationRegistry.getTargetContext
    testDB.close()
    context.deleteDatabase(DatabaseName)
  }

  @Test
  def extractForImageUri(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_video_large_preview)
    val content = ContentForUpload("test_video_large_preview.mp4", Content.Uri(uri))

    Mockito.when(uriHelper.extractMime(uri)).thenReturn(Success(Mime.Video.MP4))
    Mockito.when(uriHelper.openInputStream(uri)).thenCallRealMethod()

    for {
      videoAsset <- assetService.createAndSaveUploadAsset(content, NoEncryption, public = true, Retention.Volatile, None)
      errorOrUpdatedAsset <- assetService.createAndSavePreview(videoAsset).modelToEither
    } yield {
      lazy val errorMsg = s"Updated asset: $errorOrUpdatedAsset"
      assert(errorOrUpdatedAsset.isRight, errorMsg)
//      val details = errorOrUpdatedAsset.right.get

//      assert(details.isInstanceOf[Image], errorMsg)
//      val imageDetails = details.asInstanceOf[Image]
//      assert(imageDetails.dimensions.width > 0 && imageDetails.dimensions.height > 0, errorMsg)
    }
  }

}
