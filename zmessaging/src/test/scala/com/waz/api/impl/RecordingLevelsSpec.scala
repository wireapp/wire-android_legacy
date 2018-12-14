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
package com.waz.api.impl

import com.waz.testutils.Matchers._
import com.waz.utils.events.EventStream
import com.waz.utils.returning
import org.scalatest._
import com.waz.ZLog.ImplicitTag._
import com.waz.specs.AndroidFreeSpec

class RecordingLevelsSpec extends AndroidFreeSpec with OptionValues {
  scenario("Aggregating recording levels") {
    val stream = EventStream[Float]()
    val levels = returning(new RecordingLevels(stream).windowed(5))(_.disableAutowiring())
    def current = levels.currentValue.value.toVector

    soon(current shouldEqual Vector(0f, 0f, 0f, 0f, 0f))

    stream ! 0.1f
    current shouldEqual Vector(0.1f, 0f, 0f, 0f, 0f)

    stream ! 0.2f
    current shouldEqual Vector(0.1f, 0.2f, 0f, 0f, 0f)

    stream ! 0.3f
    current shouldEqual Vector(0.1f, 0.2f, 0.3f, 0f, 0f)

    stream ! 0.4f
    current shouldEqual Vector(0.1f, 0.2f, 0.3f, 0.4f, 0f)

    stream ! 0.5f
    current shouldEqual Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)

    stream ! 0.6f
    current shouldEqual Vector(0.2f, 0.3f, 0.4f, 0.5f, 0.6f)

    stream ! 0.7f
    current shouldEqual Vector(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
  }

  feature("Adapt overview to number of displayable levels") {
    scenario("No overview") {
      val allLevels = AudioOverview(None)
      allLevels.getLevels(3).toVector shouldEqual Vector(0f, 0f, 0f)
    }

    scenario("Empty overview") {
      val allLevels = AudioOverview(Some(Vector.empty[Float]))
      allLevels.getLevels(3).toVector shouldEqual Vector(0f, 0f, 0f)
    }

    scenario("Equal sizes") {
      val allLevels = AudioOverview(Some(Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)))
      allLevels.getLevels(9).toVector shouldEqual Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)
    }

    scenario("Adapting a larger overview (select max)") {
      val allLevels = AudioOverview(Some(Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)))
      allLevels.getLevels(3).toVector shouldEqual Vector(0.3f, 0.6f, 0.9f)

      val allLevels2 = AudioOverview(Some(Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)))
      allLevels2.getLevels(8).toVector shouldEqual Vector(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.9f)
    }

    scenario("Adapting a smaller overview (interpolate)") {
      val allLevels = AudioOverview(Some(Vector(1f, 2f, 0f)))
      allLevels.getLevels(9).toVector shouldEqual Vector(1f, 1.25f, 1.5f, 1.75f, 2f, 1.5f, 1f, 0.5f, 0f)
    }
  }
}
