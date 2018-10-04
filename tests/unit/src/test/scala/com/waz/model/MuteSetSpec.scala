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
package com.waz.model

import com.waz.specs.AndroidFreeSpec
import org.scalatest._

class MuteSetSpec extends AndroidFreeSpec {
  feature("Bitmask conversion") {
    scenario("Parse all allowed") {
      assert(MuteSet(0).isAllAllowed)
      assert(!MuteSet(1).isAllAllowed)
      assert(!MuteSet(2).isAllAllowed)
      assert(!MuteSet(3).isAllAllowed)
    }

    scenario("Parse mentions only") {
      assert(!MuteSet(0).onlyMentionsAllowed)
      assert(!MuteSet(1).onlyMentionsAllowed)
      assert(MuteSet(2).onlyMentionsAllowed)
      assert(!MuteSet(3).onlyMentionsAllowed)
    }

    scenario("Parse all muted") {
      assert(!MuteSet(0).isAllMuted)
      assert(!MuteSet(1).isAllMuted)
      assert(!MuteSet(2).isAllMuted)
      assert(MuteSet(3).isAllMuted)
    }

    scenario("Parse old muted flag") {
      assert(!MuteSet(0).oldMutedFlag)
      assert(!MuteSet(1).oldMutedFlag)
      assert(MuteSet(2).oldMutedFlag)
      assert(MuteSet(3).oldMutedFlag)
    }

    scenario("Parse and re-parse") {
      assert(MuteSet(0).toInt == 0)
      assert(MuteSet(1).toInt == 1)
      assert(MuteSet(2).toInt == 2)
      assert(MuteSet(3).toInt == 3)
    }
  }
}
