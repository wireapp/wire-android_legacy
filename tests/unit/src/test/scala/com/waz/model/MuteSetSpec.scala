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

  feature("Conversion from the conversation state") {
    // the expected results in these tests are based on
    // https://github.com/wearezeta/documentation/blob/6b1389b7bb11ca706bac540fcd0ffe6b25ed11da/topics/notifications/use-cases/001-notification-decision-tree.md

    scenario("The old muted flag") {
      val cState1 = ConversationState(muted = Some(true))
      val muteSet1 = MuteSet.resolveMuted(cState1)
      assert(muteSet1.onlyMentionsAllowed)
      assert(muteSet1.oldMutedFlag)

      val cState2 = ConversationState(muted = Some(false))
      val muteSet2 = MuteSet.resolveMuted(cState2)
      assert(muteSet2.isAllAllowed)
      assert(!muteSet2.oldMutedFlag)
    }

    scenario("The new muted status") {
      val cState1 = ConversationState(mutedStatus = Some(0))
      val muteSet1 = MuteSet.resolveMuted(cState1)
      assert(muteSet1.isAllAllowed)
      assert(!muteSet1.oldMutedFlag)

      val cState2 = ConversationState(mutedStatus = Some(2))
      val muteSet2 = MuteSet.resolveMuted(cState2)
      assert(muteSet2.onlyMentionsAllowed)
      assert(muteSet2.oldMutedFlag)

      val cState3 = ConversationState(mutedStatus = Some(3))
      val muteSet3 = MuteSet.resolveMuted(cState3)
      assert(muteSet3.isAllMuted)
      assert(muteSet3.oldMutedFlag)
    }

    scenario("Both the old flag and the new status") {
      // the conversation was set to "all allowed" on a device with the new version, and then set to "muted" on another device with the old version
      val cState1 = ConversationState(muted = Some(true), mutedStatus = Some(0))
      val muteSet1 = MuteSet.resolveMuted(cState1)
      assert(muteSet1.onlyMentionsAllowed) // the result is "mentions only" on the new version
      assert(muteSet1.oldMutedFlag) // and "muted" on the old

      // the conversation was set to "all muted" on a device with the new version, and then set to "unmuted" on another device with the old version
      val cState2 = ConversationState(muted = Some(false), mutedStatus = Some(3))
      val muteSet2 = MuteSet.resolveMuted(cState2)
      assert(muteSet2.isAllAllowed) // the result is "all allowed" on both versions
      assert(!muteSet2.oldMutedFlag)

      // now let's say the conversation from the previous example was "muted" again on the device with the old version
      val cState3 = ConversationState(muted = Some(true), mutedStatus = Some(3))
      val muteSet3 = MuteSet.resolveMuted(cState3)
      assert(muteSet3.isAllMuted) // the result is "all muted" on the new version
      assert(muteSet3.oldMutedFlag) // and "muted" on the old

      // as above, but with "only mentions" on the new version
      val cState4 = ConversationState(muted = Some(false), mutedStatus = Some(2))
      val muteSet4 = MuteSet.resolveMuted(cState4)
      assert(muteSet4.isAllAllowed) // the result is "all allowed" on both versions
      assert(!muteSet4.oldMutedFlag)

      // now let's say the conversation from the previous example was "muted" again on the device with the old version
      val cState5 = ConversationState(muted = Some(true), mutedStatus = Some(2))
      val muteSet5 = MuteSet.resolveMuted(cState5)
      assert(muteSet5.onlyMentionsAllowed) // the result is "mentions only" on the new version - the information about the original state was preserved
      assert(muteSet5.oldMutedFlag) // and "muted" on the old
    }
  }
}
