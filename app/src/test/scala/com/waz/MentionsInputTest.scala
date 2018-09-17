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
package com.waz

import com.waz.model.UserId
import com.waz.zclient.cursor.Mention
import com.waz.zclient.cursor.Mention.Replacement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.scalatest.junit.JUnitSuite

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = Config.NONE)
class MentionsInputTest extends JUnitSuite {

  @Test
  def testWithAtAndSelectorAtEnd(): Unit = {
    val input = "123 @456"
    assert(Mention.mentionMatch(input, input.length).nonEmpty)
    assert(Mention.mentionQuery(input, input.length).contains("456"))
  }

  @Test
  def testWithAtAndSelectorMiddle(): Unit = {
    val input = "123 @456 789"
    assert(Mention.mentionMatch(input, 8).nonEmpty)
    assert(Mention.mentionQuery(input, 8).contains("456"))
  }

  @Test
  def testWithAtAndSelectorAfterWord(): Unit = {
    val input = "123 @456 789"
    assert(Mention.mentionMatch(input, input.length).isEmpty)
    assert(Mention.mentionQuery(input, input.length).isEmpty)
  }

  @Test
  def getValidMentionReplacement(): Unit = {
    val input = "123 @456 789"
    val userId = UserId("abc")
    val userName = "name"

    Mention.getMention(input, 8, userId, userName) match {
      case None => assert(false)
      case Some((Mention(mStart, length, uid), Replacement(rStart, rEnd, replacement))) =>
        assert(mStart == 4)
        assert(length == 5)
        assert(rStart == 4)
        assert(rEnd == 8)
        assert(uid.contains(userId))
        assert(replacement == "@name")
    }
  }

  @Test
  def getInvalidMentionReplacement(): Unit = {
    val input = "123 @456 789"
    val userId = UserId("abc")
    val userName = "name"

    assert(Mention.getMention(input, 3, userId, userName).isEmpty)
  }
}
