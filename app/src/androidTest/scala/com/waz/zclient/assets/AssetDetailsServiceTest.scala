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
package com.waz.zclient.assets

import java.io._

import android.support.test.InstrumentationRegistry._
import android.support.test.filters.MediumTest
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import com.waz.model.Mime
import com.waz.service.assets2.{AudioDetails, ImageDetails, VideoDetails}
import com.waz.utils.IoUtils
import com.waz.zclient.TestUtils._
import com.waz.zclient.dev.test.R
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import com.waz.service.assets.{AudioDetails, Content, ImageDetails, VideoDetails}
import org.junit.Rule

import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[AndroidJUnit4])
@MediumTest
class AssetDetailsServiceTest {

  val uriHelper = new AndroidUriHelper(getContext)
  val detailsService = new AssetDetailsServiceImpl(uriHelper)(getContext, global)

  @Rule
  def permissions: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

  @Test
  def extractForImageUri(): Unit = {
    val uri = getResourceUri(getContext, R.raw.test_img)
    val (details, mime) = detailsService.extract(Content.Uri(uri))
    assert(Mime.Image.Png == mime, s"the mime type should be Png, but is $mime")
    assert(details.isInstanceOf[ImageDetails], s"the details should be ImageDetails, but is ${details.getClass.getName}")
    val imageDetails = details.asInstanceOf[ImageDetails]
    assert(
      imageDetails.dimensions.width > 0 && imageDetails.dimensions.height > 0,
      s"the image dimensions should be >0, are $imageDetails"
    )
  }

  @Test
  def extractForVideoUri(): Unit = {
    val uri = getResourceUri(getContext, R.raw.test_video)
    val (details, mime) = detailsService.extract(Content.Uri(uri))
    assert(Mime.Video.MP4 == mime, s"the mime type should be MP4, but is $mime")
    assert(details.isInstanceOf[VideoDetails], s"the details should be VideoDetails, but is ${details.getClass.getName}")
    val videoDetails = details.asInstanceOf[VideoDetails]
    assert(
      videoDetails.dimensions.width > 0 && videoDetails.dimensions.height > 0 && !videoDetails.duration.isZero,
      s"the video dimensions and duration should be >0 , are $videoDetails"
    )
  }

  @Test
  def extractForVideoFile(): Unit = {
    val uri = getResourceUri(getContext, R.raw.test_video)
    val file = new File(getContext.getExternalCacheDir, "test_video")
    IoUtils.copy(uriHelper.openInputStream(uri).get,  new FileOutputStream(file))
    val (details, mime) = detailsService.extract(Content.File(Mime.Video.MP4, file))
    assert(Mime.Video.MP4 == mime, s"the mime type should be MP4, but is $mime")
    assert(details.isInstanceOf[VideoDetails], s"the details should be VideoDetails, but is ${details.getClass.getName}")
    val videoDetails = details.asInstanceOf[VideoDetails]
    assert(
      videoDetails.dimensions.width > 0 && videoDetails.dimensions.height > 0 && !videoDetails.duration.isZero,
      s"the video dimensions and duration should be >0 , are $videoDetails"
    )
  }

  @Test
  def extractForAudioUri(): Unit = {
    val uri = getResourceUri(getContext, R.raw.test_audio)
    val (details, mime) = detailsService.extract(Content.Uri(uri))
    assert(Mime.Audio.WAV == mime, s"the mime type should be WAV, but is $mime")
    assert(details.isInstanceOf[AudioDetails], s"the details should be AudioDetails, but is ${details.getClass.getName}")
    val audioDetails = details.asInstanceOf[AudioDetails]
    assert(
      audioDetails.loudness.levels.nonEmpty && !audioDetails.duration.isZero,
      s"the audio lludness and duration should be >0 , are $audioDetails"
    )
  }

  @Test
  def extractForAudioFile(): Unit = {
    val uri = getResourceUri(getContext, R.raw.test_audio)
    val testAudio = new File(getInstrumentation.getContext.getExternalCacheDir, "test_audio")
    testAudio.createNewFile()
    IoUtils.copy(uriHelper.openInputStream(uri).get, new FileOutputStream(testAudio))
    val (details, mime) = detailsService.extract(Content.File(Mime.Audio.WAV, testAudio))
    assert(Mime.Audio.WAV == mime, s"the mime type should be WAV, but is $mime")
    assert(details.isInstanceOf[AudioDetails], s"the details should be AudioDetails, but is ${details.getClass.getName}")
    val audioDetails = details.asInstanceOf[AudioDetails]
    assert(
      audioDetails.loudness.levels.nonEmpty && !audioDetails.duration.isZero,
      s"the audio lludness and duration should be >0 , are $audioDetails"
    )
  }

}
