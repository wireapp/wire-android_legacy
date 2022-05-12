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
package com.waz.zclient.messages.part

import com.waz.model.{Mention, UserId}
import com.waz.zclient.cursor.MentionUtils
import com.waz.zclient.cursor.MentionUtils.Replacement
import com.waz.zclient.messages.parts.TextPartView
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class MentionsInputTest extends JUnitSuite {

  @Test
  def testWithAtAndSelectorAtEnd(): Unit = {
    val input = "123 @456"
    assert(MentionUtils.mentionMatch(input, input.length).nonEmpty)
    assert(MentionUtils.mentionQuery(input, input.length).contains("456"))
  }

  @Test
  def testWithAtAndSelectorMiddle(): Unit = {
    val input = "123 @456 789"
    assert(MentionUtils.mentionMatch(input, 8).nonEmpty)
    assert(MentionUtils.mentionQuery(input, 8).contains("456"))
  }

  @Test
  def testWithAtAndSelectorAfterWord(): Unit = {
    val input = "123 @456 789"
    assert(MentionUtils.mentionMatch(input, input.length).isEmpty)
    assert(MentionUtils.mentionQuery(input, input.length).isEmpty)
  }

  @Test
  def getValidMentionReplacement(): Unit = {
    val input = "123 @456 789"
    val userId = UserId("abc")
    val userName = "name"

    MentionUtils.getMention(input, 8, userId, userName) match {
      case None => assert(false)
      case Some((Mention(uid, mStart, length), Replacement(rStart, rEnd, replacement))) =>
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

    assert(MentionUtils.getMention(input, 3, userId, userName).isEmpty)
  }

  @Test
  def replaceOneMention(): Unit = {
    val input = "123 @456 789"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)
    assert(holders.head.mention == mention)
    assert(holders.head.handle == handle)

    assert(replaceString.contains(holders.head.uuid))
    assert(!replaceString.contains(handle))
  }

  @Test
  def replaceTwoMentions(): Unit = {
    val input = "123 @456 789 @aaa bbb"

    val handle1 = "@456"
    val mention1 = Mention(Some(UserId()), input.indexOf(handle1), handle1.length)
    val handle2 = "@aaa"
    val mention2 = Mention(Some(UserId()), input.indexOf(handle2), handle2.length)

    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention1, mention2))

    assert(holders.size == 2)

    assert(holders.head.mention == mention1)
    assert(holders.head.handle == handle1)

    assert(replaceString.contains(holders.head.uuid))
    assert(!replaceString.contains(handle1))

    assert(holders.tail.head.mention == mention2)
    assert(holders.tail.head.handle == handle2)

    assert(replaceString.contains(holders.tail.head.uuid))
    assert(!replaceString.contains(handle2))
  }

  @Test
  def replaceOnlyMention(): Unit = {
    val input = "@456"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)
    assert(holders.head.mention == mention)
    assert(holders.head.handle == handle)

    assert(replaceString == holders.head.uuid)
  }

  @Test
  def replaceMentionAtStart(): Unit = {
    val input = "@456 aaa"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)
    assert(holders.head.mention == mention)
    assert(holders.head.handle == handle)

    assert(replaceString.contains(holders.head.uuid))
    assert(!replaceString.contains(handle))
  }

  @Test
  def replaceMentionAtEnd(): Unit = {
    val input = "aaa @456"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)
    assert(holders.head.mention == mention)
    assert(holders.head.handle == handle)

    assert(replaceString.contains(holders.head.uuid))
    assert(!replaceString.contains(handle))
  }

  @Test
  def replaceThreeMentions(): Unit = {
    val input = "@1230 456 @789 aaa @bb"

    val handle0 = "@1230"
    val mention0 = Mention(Some(UserId()), input.indexOf(handle0), handle0.length)
    val handle1 = "@789"
    val mention1 = Mention(Some(UserId()), input.indexOf(handle1), handle1.length)
    val handle2 = "@bb"
    val mention2 = Mention(Some(UserId()), input.indexOf(handle2), handle2.length)

    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention0, mention1, mention2))

    assert(holders.size == 3)

    assert(holders(0).mention == mention0)
    assert(holders(0).handle == handle0)

    assert(replaceString.contains(holders(0).uuid))
    assert(!replaceString.contains(handle0))

    assert(holders(1).mention == mention1)
    assert(holders(1).handle == handle1)

    assert(replaceString.contains(holders(1).uuid))
    assert(!replaceString.contains(handle1))

    assert(holders(2).mention == mention2)
    assert(holders(2).handle == handle2)

    assert(replaceString.contains(holders(2).uuid))
    assert(!replaceString.contains(handle2))
  }

  @Test
  def replaceMentionsWithOffset(): Unit = {
    val input = "@1230 456 @789 aaa @bb"

    val offset = 50

    val handle0 = "@1230"
    val mention0 = Mention(Some(UserId()), input.indexOf(handle0) + offset, handle0.length)
    val handle1 = "@789"
    val mention1 = Mention(Some(UserId()), input.indexOf(handle1) + offset, handle1.length)
    val handle2 = "@bb"
    val mention2 = Mention(Some(UserId()), input.indexOf(handle2) + offset, handle2.length)

    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention0, mention1, mention2), offset)

    assert(holders.size == 3)

    assert(holders(0).mention == mention0)
    assert(holders(0).handle == handle0)

    assert(replaceString.contains(holders(0).uuid))
    assert(!replaceString.contains(handle0))

    assert(holders(1).mention == mention1)
    assert(holders(1).handle == handle1)

    assert(replaceString.contains(holders(1).uuid))
    assert(!replaceString.contains(handle1))

    assert(holders(2).mention == mention2)
    assert(holders(2).handle == handle2)

    assert(replaceString.contains(holders(2).uuid))
    assert(!replaceString.contains(handle2))
  }

  @Test
  def replaceOneMentionTwice(): Unit = {
    val input = "1234 @aaa 56 @aaa 789"
    val handle = "@aaa"
    val userId = UserId()
    val mention0 = Mention(Some(userId), input.indexOf(handle), handle.length)
    val mention1 = Mention(Some(userId), input.lastIndexOf(handle), handle.length)

    assert(mention0.start != mention1.start)

    val (_, holders) = TextPartView.replaceMentions(input, Seq(mention0, mention1))

    assert(holders.size == 2)
    assert(holders(0).handle == holders(1).handle)
    assert(holders(0).uuid != holders(1).uuid)
    assert(holders(0).mention == mention0)
    assert(holders(1).mention == mention1)
  }

  @Test
  def restoreOneMention(): Unit = {
    val input = "123 @456 789"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)

    val updated = TextPartView.updateMentions(replaceString, holders, 0)

    assert(updated.size == 1)
    assert(mention == updated.head)
  }

  @Test
  def restoreOneMentionWithChange(): Unit = {
    val input = "123 @456 789"
    val handle = "@456"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))

    assert(holders.size == 1)

    val newText = "zzz "
    val updated = TextPartView.updateMentions(newText + replaceString, holders)

    assert(updated.size == 1)
    assert(mention.userId == updated.head.userId)
    assert(mention.start + newText.length == updated.head.start)
    assert(mention.length == updated.head.length)
  }

  @Test
  def restoreTwoMentionsWithChange(): Unit = {
    val input = "123 @456 789 @aa bbb"
    val handle0 = "@456"
    val mention0 = Mention(Some(UserId()), input.indexOf(handle0), handle0.length)
    val handle1 = "@aa"
    val mention1 = Mention(Some(UserId()), input.indexOf(handle1), handle1.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention0, mention1))

    assert(holders.size == 2)

    val newText = "zzz "
    val updated = TextPartView.updateMentions(newText + replaceString, holders, 0)

    assert(updated.size == 2)
    assert(mention0.userId == updated(0).userId)
    assert(mention0.start + newText.length == updated(0).start)
    assert(mention0.length == updated(0).length)
    assert(mention1.userId == updated(1).userId)
    assert(mention1.start + newText.length == updated(1).start)
    assert(mention1.length == updated(1).length)
  }

  @Test
  def restoreTwoMentionsWithChangeAndOffset(): Unit = {
    val input = "123 @456 789 @aa bbb"

    val offset = 50

    val handle0 = "@456"
    val mention0 = Mention(Some(UserId()), input.indexOf(handle0) + offset, handle0.length)
    val handle1 = "@aa"
    val mention1 = Mention(Some(UserId()), input.indexOf(handle1) + offset, handle1.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention0, mention1), offset)

    assert(holders.size == 2)

    val newText = "zzz "
    val updated = TextPartView.updateMentions(newText + replaceString, holders, offset)
    assert(updated.size == 2)
    assert(mention0.userId == updated(0).userId)
    assert(mention0.start + newText.length == updated(0).start)
    assert(mention0.length == updated(0).length)
    assert(mention1.userId == updated(1).userId)
    assert(mention1.start + newText.length == updated(1).start)
    assert(mention1.length == updated(1).length)
  }

  @Test
  def restoreForNewlines(): Unit = {
    val input = "aaa\n\n\n@user"
    val handle = "@user"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)

    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))
    val changedString = replaceString.replace("\n\n\n", "\n")
    val updated = TextPartView.updateMentions(changedString, holders)

    assert(updated.size == 1)
    assert(updated.head.userId == mention.userId)
    assert(updated.head.start == mention.start - 2)
  }

  @Test
  def handleWhenMentionIsDeleted(): Unit = {
    val input = "aaa @user bbb"
    val handle = "@user"
    val mention = Mention(Some(UserId()), input.indexOf(handle), handle.length)
    val (replaceString, holders) = TextPartView.replaceMentions(input, Seq(mention))
    assert(holders.size == 1)

    val changedString = replaceString.replace(holders.head.uuid, "markdown")
    val updated = TextPartView.updateMentions(changedString, holders)

    assert(updated.isEmpty)
  }
}
