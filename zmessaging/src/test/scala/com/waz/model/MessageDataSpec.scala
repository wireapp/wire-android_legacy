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

import com.waz.api.Message
import com.waz.model.GenericContent.LinkPreview
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.wrappers.URI
import org.scalatest._


class MessageDataSpec extends AndroidFreeSpec {
  feature("Message content") {
    scenario("Wrap empty message in a text content") {
      val expected = MessageContent(Message.Part.Type.TEXT_EMOJI_ONLY, "") // TODO: empty content should return TEXT, not TEXT_EMOJI_ONLY

      val result = MessageData.messageContent("", Nil)
      result._1 shouldEqual Message.Type.TEXT
      result._2 shouldEqual Seq(expected)
    }

    scenario("Insert a mention in the content") {
      val userId = UserId()
      val text = "Aaa @user aaa"
      val mention = Mention(Some(userId), 4, 4)

      val expected = MessageContent(Message.Part.Type.TEXT, text, mentions = Seq(mention))

      val result = MessageData.messageContent(text, Seq(mention))
      result._1 shouldEqual Message.Type.TEXT
      result._2 shouldEqual Seq(expected)
    }

    scenario("Insert two mentions in the content") {
      val userId1 = UserId()
      val userId2 = UserId()
      val text = "Aaa @user1 aaa @user2 aaa"
      val mentions = Seq(Mention(Some(userId1), 4, 5), Mention(Some(userId2), 10, 5))

      val expected = MessageContent(Message.Part.Type.TEXT, text, mentions = mentions)

      val result = MessageData.messageContent(text, mentions)
      result._1 shouldEqual Message.Type.TEXT
      result._2 shouldEqual Seq(expected)
    }

    scenario("Insert two mentions and a link") {
      val userId1 = UserId()
      val userId2 = UserId()
      val text = "Aaa @user1 aaa http://bit.ly aaa @user2 aaa"
      val mentions = Seq(Mention(Some(userId1), 4, 5), Mention(Some(userId2), 32, 5))
      val linkPreview = LinkPreview(URI.parse("http://bit.ly"), 15)

      val expected = List(
        MessageContent(Message.Part.Type.TEXT, "Aaa @user1 aaa ", mentions = Seq(mentions(0))),
        MessageContent(Message.Part.Type.WEB_LINK, "http://bit.ly", mentions = Nil),
        MessageContent(Message.Part.Type.TEXT, " aaa @user2 aaa", mentions = Seq(mentions(1)))
      )

      val result = MessageData.messageContent(text, mentions, links = Seq(linkPreview), weblinkEnabled = true)

      result._1 shouldEqual Message.Type.RICH_MEDIA
      result._2 shouldEqual expected
    }
  }

  // Usefulness of these tests is limited: the default charset for Java and Scala is UTF-16.
  // Only when we're actually on Android, the default charset becomes UTF-8.
  feature("Adjust mentions") {
   scenario("Adjust a mention in a Latin text to UTF-16") {
      val handle = "@user"
      val text = s"aaa $handle bbb"
      val start = text.indexOf(handle)
      val mention = Mention(Some(UserId()), start, handle.length)
      val mentions = Seq(mention)
      println(s"mentions: $mentions")
      val adjusted = MessageData.adjustMentions(text, mentions, forSending = true)
      println(s"adjusted: $adjusted")
      adjusted shouldEqual mentions
    }

    scenario("Adjust two mentions in a Latin text to UTF-16"){
      val handle1 = "@user1"
      val handle2 = "@user2"
      val text = s"Aaa $handle1 aaa $handle2 aaa"
      val mention1 = Mention(Some(UserId()), text.indexOf(handle1), handle1.length)
      val mention2 = Mention(Some(UserId()), text.indexOf(handle2), handle2.length)
      val mentions = Seq(mention1, mention2)
      println(s"mentions: $mentions")
      val adjusted = MessageData.adjustMentions(text, mentions, forSending = true)
      println(s"adjusted: $adjusted")
      adjusted shouldEqual mentions
    }

    scenario("Adjust a mention with an emoji to UTF-16") {
      val handle = "@user"
      val text = s"aaa 😁 $handle bbb"
      val start = text.indexOf(handle)
      val mention = Mention(Some(UserId()), start, handle.length)
      val mentions = Seq(mention)
      println(s"mentions: $mentions")
      val adjusted = MessageData.adjustMentions(text, mentions, forSending = true)
      println(s"adjusted: $adjusted")
      adjusted shouldEqual mentions
    }
  }
}
