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

import com.waz.service.images.ImageAssetGenerator.SmallProfileOptions
import com.waz.ui.MemoryImageCache.BitmapRequest
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class CompressionOptionsSpec extends FeatureSpec with Matchers with BeforeAndAfter with GeneratorDrivenPropertyChecks {

  val opts = CompressionOptions(310 * 1024, 1448, 45, forceLossy = false, cropToSquare = false, BitmapRequest.Regular())

  val sizeGen = Gen.choose(5, 50000)

  scenario("calculated scaled size should satisfy shouldScale") {

    forAll(sizeGen, sizeGen) { (w: Int, h: Int) =>
      if (opts.shouldScaleOriginalSize(w, h)) {
        val (sw, sh) = opts.calculateScaledSize(w, h)

        withClue(s"input: ($w, $h), scaled: ($sw, $sh)") {
          opts.shouldScaleOriginalSize(sw, sh) shouldEqual false
        }
      }
    }
  }

  scenario("cropping to square should always result in square images") {
    forAll(sizeGen, sizeGen) { (w: Int, h: Int) =>
      val (sw, sh) = SmallProfileOptions.calculateScaledSize(w, h)
      withClue(s"input: ($w, $h), scaled: ($sw, $sh)") {
        sw shouldEqual sh
        sw should be <= 280
        if (w >= 280 && h >= 280) sw shouldEqual 280 else sw shouldEqual math.min(w, h)
      }
    }
  }
}
