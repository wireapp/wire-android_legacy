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
package com.waz.utils

import com.waz.ZLog._
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.util.Failure

@Ignore class LoggedTrySpec extends FeatureSpec with Matchers with RobolectricTests {

  implicit val logTag = "test": LogTag

  feature("error boxing") {

    scenario("box OoM") {
      intercept[BoxedError] {
        BoxedError.boxOoM(throw new OutOfMemoryError("test"))
      }
    }

    scenario("don't box other errors when asking for OoM") {
      intercept[Error] {
        BoxedError.boxOoM(throw new Error("test"))
      }
    }

    scenario("box any fatal error") {
      intercept[BoxedError] {
        BoxedError.boxFatal(throw new OutOfMemoryError("test"))
      }
      intercept[BoxedError] {
        BoxedError.boxFatal(throw new InterruptedException("test"))
      }
    }
  }

  feature("Logged try") {

    scenario("catch regular exception") {
      LoggedTry(throw new NullPointerException) match {
        case Failure(_: NullPointerException) => // expected
        case res => fail(s"unexpected result: $res")
      }
    }

    scenario("catch error") {
      LoggedTry(throw new OutOfMemoryError) match {
        case Failure(BoxedError(_: OutOfMemoryError)) => // expected
        case res => fail(s"unexpected result: $res")
      }
    }

    scenario("catch boxed error") {
      LoggedTry(BoxedError.boxOoM(throw new OutOfMemoryError)) match {
        case Failure(BoxedError(_: OutOfMemoryError)) => // expected
        case res => fail(s"unexpected result: $res")
      }
    }
  }
}
