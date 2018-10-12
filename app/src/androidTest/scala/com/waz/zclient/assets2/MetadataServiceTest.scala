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

import android.support.test.InstrumentationRegistry._
import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4
import com.waz.model.errors._
import com.waz.zclient.TestUtils._
import com.waz.zclient.dev.test.R
import org.junit.Test
import org.junit.runner.RunWith

import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[AndroidJUnit4])
@MediumTest
class MetadataServiceTest {

  val metadataService = new MetadataServiceImpl(
    new AndroidUriHelper(getContext),
    new AndroidMetadataRetriever(getTargetContext)
  )

  @Test
  def extractForImage(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_img)
    for {
      errorOrMeta <- metadataService.extractForImage(uri).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted metadata: $errorOrMeta"
      assert(errorOrMeta.isRight, errorMsg)
      val meta = errorOrMeta.right.get
      assert(meta.dimensions.width > 0 && meta.dimensions.height > 0, errorMsg)
    }
  }

  @Test
  def extractForImage2(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_video)
    for {
      errorOrMeta <- metadataService.extractForImage(uri).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted metadata: $errorOrMeta"
      assert(errorOrMeta.isLeft, errorMsg)
    }
  }

  @Test
  def extractForVideo(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_video)
    for {
      errorOrMeta <- metadataService.extractForVideo(uri).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted metadata: $errorOrMeta"
      assert(errorOrMeta.isRight, errorMsg)
      val meta = errorOrMeta.right.get
      assert(meta.dimensions.width > 0 && meta.dimensions.height > 0 && !meta.duration.isZero, errorMsg)
    }
  }

  @Test
  def extractForVideo2(): Unit = asyncTest {
    val uri = getResourceUri(getContext, R.raw.test_img)
    for {
      errorOrMeta <- metadataService.extractForVideo(uri).modelToEither
    } yield {
      lazy val errorMsg = s"Extracted metadata: $errorOrMeta"
      assert(errorOrMeta.isLeft, errorMsg)
    }
  }

}
