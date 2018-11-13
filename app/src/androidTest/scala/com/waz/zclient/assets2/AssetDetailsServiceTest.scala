/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.assets2

import java.io._

import android.support.test.InstrumentationRegistry._
import android.support.test.filters.MediumTest
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import com.waz.model.Mime
import com.waz.model.errors._
import com.waz.service.assets2.Asset.{Audio, Image, Video}
import com.waz.service.assets2.Content
import com.waz.utils.IoUtils
import com.waz.zclient.TestUtils._
import com.waz.zclient.dev.test.R
import org.junit.Test
import org.junit.runner.RunWith

import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[AndroidJUnit4])
@MediumTest
class AssetDetailsServiceTest {

  val uriHelper = new AndroidUriHelper(getContext)
  val detailsService = new AssetDetailsServiceImpl(uriHelper)(getContext, global)

  import android.Manifest
  import org.junit.Rule

  @Rule
  def permissions: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

  @Test
  def extractForImageUri(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_img)
    for {
      errorOrDetails <- detailsService.extract(Mime.Image.Png, Content.Uri(uri)).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted details: $errorOrDetails"
      assert(errorOrDetails.isRight, errorMsg)
      val details = errorOrDetails.right.get

      assert(details.isInstanceOf[Image], errorMsg)
      val imageDetails = details.asInstanceOf[Image]
      assert(imageDetails.dimensions.width > 0 && imageDetails.dimensions.height > 0, errorMsg)
    }
  }

  @Test
  def extractForVideoUri(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_video)
    for {
      errorOrDetails <- detailsService.extract(Mime.Video.MP4, Content.Uri(uri)).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted details: $errorOrDetails"
      assert(errorOrDetails.isRight, errorMsg)
      val details = errorOrDetails.right.get

      assert(details.isInstanceOf[Video], errorMsg)
      val videoDetails = details.asInstanceOf[Video]
      assert(videoDetails.dimensions.width > 0 && videoDetails.dimensions.height > 0 && !videoDetails.duration.isZero, errorMsg)
    }
  }

  @Test
  def extractForVideoFile(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_video)
    val file = new File(getContext.getExternalCacheDir, "test_video")
    IoUtils.copy(uriHelper.openInputStream(uri).get,  new FileOutputStream(file))

    for {
      errorOrDetails <- detailsService.extract(Mime.Video.MP4, Content.File(Mime.Video.MP4, file)).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted details: $errorOrDetails"
      assert(errorOrDetails.isRight, errorMsg)
      val details = errorOrDetails.right.get

      assert(details.isInstanceOf[Video], errorMsg)
      val videoDetails = details.asInstanceOf[Video]
      assert(videoDetails.dimensions.width > 0 && videoDetails.dimensions.height > 0 && !videoDetails.duration.isZero, errorMsg)
    }
  }

  @Test
  def extractForAudioUri(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_audio)
    for {
      errorOrDetails <- detailsService.extract(Mime.Audio.WAV, Content.Uri(uri)).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted details: $errorOrDetails"
      assert(errorOrDetails.isRight, errorMsg)
      val details = errorOrDetails.right.get

      assert(details.isInstanceOf[Audio], errorMsg)
      val audioDetails = details.asInstanceOf[Audio]
      assert(audioDetails.loudness.levels.nonEmpty && !audioDetails.duration.isZero, errorMsg)
    }
  }

  @Test
  def extractForAudioFile(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_audio)
    val testAudio = new File(getInstrumentation.getContext.getExternalCacheDir, "test_audio")
    testAudio.createNewFile()
    IoUtils.copy(uriHelper.openInputStream(uri).get, new FileOutputStream(testAudio))

    for {
      errorOrDetails <- detailsService.extract(Mime.Audio.WAV, Content.File(Mime.Audio.WAV, testAudio)).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted details: ${errorOrDetails.left.get.cause.get.toString}"
      assert(errorOrDetails.isRight, errorMsg)
      val details = errorOrDetails.right.get

      assert(details.isInstanceOf[Audio], errorMsg)
      val audioDetails = details.asInstanceOf[Audio]
      assert(audioDetails.loudness.levels.nonEmpty && !audioDetails.duration.isZero, errorMsg)
    }
  }


}
