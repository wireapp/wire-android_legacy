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
package com.waz.service.media

import com.waz.api.Message.Part.Type._
import com.waz.model.{Mention, MessageContent, UserId}
import org.scalacheck.Gen
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, TableDrivenPropertyChecks}
import RichMediaContentParser._
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.wrappers.URI

import scala.io.{Codec, Source}

class RichMediaContentParserSpec extends AndroidFreeSpec with TableDrivenPropertyChecks with GeneratorDrivenPropertyChecks {

  feature("match links") {

    scenario("match correct youtube links") {
      val links = List(
        "http://youtube.com/watch?v=c0KYU2j0TM4",
        "http://www.youtube.com/watch?v=c0KYU2j0TM4",
        "http://www.youtube.com/watch?v=c0KYU2j0TM4#t=100",
        "https://www.youtube.com/watch?v=c0KYU2j0TM4",
        "https://es.youtube.com/watch?v=c0KYU2j0TM4",
        "http://youtube.com/watch?v=c0KYU2j0TM4&wadsworth=1",
        "http://youtube.com/watch?v=c0KYU2j0TM4&wadsworth=1#t=100",
        "http://m.youtube.com/watch%3Fv%3D84zY33QZO5o",
        "https://www.youtube.com/watch?v=HuvLUhuo52w&feature=youtu.be",
        "http://youtu.be/c0KYU2j0TM4#t=100",
        "youtu.be/c0KYU2j0TM4#t=100"
      )

      links foreach { link =>
        findMatches(link).toList shouldEqual List((0, link.length, YOUTUBE))
      }
    }

    scenario("don't match invalid youtube links") {
      val links = List(
        "http://m.youtube.com/watch?v=84zY33QZ&ved=0CB0QtwlwAA"
      )

      links foreach { link => findMatches(link) should be(empty) }
    }

    scenario("match soundcloud link") {
      val link = "https://soundcloud.com/majorlazer/major-lazer-dj-snake-lean-on-feat-mo"
      findMatches(link).toList shouldEqual List((0, link.length, SOUNDCLOUD))
    }

    scenario("match weblinks") {
      val link = "https://www.google.de/url?sa=t&source=web&rct=j&ei=s-EzVMzyEoLT7Qb7loC4CQ&url=http://m.youtube.com/watch%3Fv%3D84zY33QZO5o&ved=0CB0QtwIwAA&usg=AFQjCNEgZ6mQSXLbKY1HAhVOEiAwHtTIvA"
      findMatches(link, weblinkEnabled = true).toList shouldEqual List((0, link.length, WEB_LINK))
    }

    scenario("match weblinks with HTTP") {
      val link = "HTTP://Wire.com"
      findMatches(link, weblinkEnabled = true).toList shouldEqual List((0, link.length, WEB_LINK))
    }

    scenario("match weblinks without http") {
      val link = "wire.com"
      findMatches(link, weblinkEnabled = true).toList shouldEqual List((0, link.length, WEB_LINK))
    }

    scenario("ignore blacklisted weblinks") {
      val link = "giphy.com"
      findMatches(link, weblinkEnabled = true).toList shouldBe empty
    }
  }

  feature("id parsing") {

    scenario("parse youtube id") {
      Map(
        "http://youtube.com/watch?v=c0KYU2j0TM4" -> "c0KYU2j0TM4",
        "http://www.youtube.com/watch?v=c0KYU2j0TM4" -> "c0KYU2j0TM4",
        "http://www.youtube.com/watch?v=c0KYU2j0TM4#t=100" -> "c0KYU2j0TM4",
        "https://www.youtube.com/watch?v=c0KYU2j0TM4" -> "c0KYU2j0TM4",
        "https://es.youtube.com/watch?v=c0KYU2j0TM4" -> "c0KYU2j0TM4",
        "http://youtube.com/watch?v=c0KYU2j0TM4&wadsworth=1" -> "c0KYU2j0TM4",
        "http://youtube.com/watch?wadsworth=1&v=c0KYU2j0TM4" -> "c0KYU2j0TM4",
        "http://youtube.com/watch?v=c0KYU2j0TM4&wadsworth=1#t=100" -> "c0KYU2j0TM4",
        "http://m.youtube.com/watch%3Fv%3D84zY33QZO5o" -> "84zY33QZO5o",
        "https://www.youtube.com/watch?v=HuvLUhuo52w&feature=youtu.be" -> "HuvLUhuo52w",
        "http://youtu.be/c0KYU2j0TM4#t=100" -> "c0KYU2j0TM4"
      ) foreach {
        case (url, id) => RichMediaContentParser.youtubeVideoId(url) shouldEqual Some(id)
      }
    }
  }

  feature("split content") {
    scenario("single youtube link") {
      splitContent("https://www.youtube.com/watch?v=MWdG413nNkI") shouldEqual List(MessageContent(YOUTUBE, "https://www.youtube.com/watch?v=MWdG413nNkI"))
    }

    scenario("text with youtube link") {
      splitContent("Here is some text. https://www.youtube.com/watch?v=MWdG413nNkI") shouldEqual List(MessageContent(TEXT, "Here is some text. "), MessageContent(YOUTUBE, "https://www.youtube.com/watch?v=MWdG413nNkI"))
    }

    scenario("don't split proper uri") {
      splitContent("https://www.youtube.com/watch?v=HuvLUhuo52w&feature=youtu.be") shouldEqual List(MessageContent(YOUTUBE, "https://www.youtube.com/watch?v=HuvLUhuo52w&feature=youtu.be"))
    }

    scenario("don't extract embeded url") {
      splitContent("https://www.google.de/url?sa=t&source=web&rct=j&ei=s-EzVMzyEoLT7Qb7loC4CQ&url=http://m.youtube.com/watch%3Fv%3D84zY33QZO5o&ved=0CB0QtwIwAA&usg=AFQjCNEgZ6mQSXLbKY1HAhVOEiAwHtTIvA", weblinkEnabled = true) shouldEqual
        List(MessageContent(WEB_LINK, "https://www.google.de/url?sa=t&source=web&rct=j&ei=s-EzVMzyEoLT7Qb7loC4CQ&url=http://m.youtube.com/watch%3Fv%3D84zY33QZO5o&ved=0CB0QtwIwAA&usg=AFQjCNEgZ6mQSXLbKY1HAhVOEiAwHtTIvA"))
    }

    scenario("text interleaved with multiple youtube links") {
      splitContent("Here is some text. https://www.youtube.com/watch?v=MWdG413nNkI more text https://www.youtube.com/watch?v=c0KYU2j0TM4 and even more") shouldEqual List(
        MessageContent(TEXT, "Here is some text. "),
        MessageContent(YOUTUBE, "https://www.youtube.com/watch?v=MWdG413nNkI"),
        MessageContent(TEXT, " more text "),
        MessageContent(YOUTUBE, "https://www.youtube.com/watch?v=c0KYU2j0TM4"),
        MessageContent(TEXT, " and even more")
      )
    }

    scenario("don't extract a link from a mention") {
      val mentionStr = "@[nqa2](http://google.com)"
      val text = s"aaa $mentionStr bbb"
      val mention = Mention(Some(UserId()), 4, mentionStr.length)
      splitContent(text, Seq(mention), weblinkEnabled = true) shouldEqual List(MessageContent(TEXT, text, mentions = Seq(mention)))
    }

    scenario("don't extract a link from a mention being the whole message") {
      val mentionStr = "@[nqa2](http://google.com)"
      val mention = Mention(Some(UserId()), 0, mentionStr.length)
      splitContent(mentionStr, Seq(mention), weblinkEnabled = true) shouldEqual List(MessageContent(TEXT, mentionStr, mentions = Seq(mention)))
    }

    scenario("cut short a link if it has a mention inside") {
      val mentionStr = "@nqa"
      val text = s"[click here](http://google.com/?$mentionStr)"
      val mention = Mention(Some(UserId()), text.indexOf(mentionStr), mentionStr.length)
      splitContent(text, Seq(mention), weblinkEnabled = true) shouldEqual List(
        MessageContent(TEXT, "[click here]("),
        MessageContent(WEB_LINK, "http://google.com/?"),
        MessageContent(TEXT, s"$mentionStr)", mentions = Seq(mention))
      )
    }

    scenario("don't extract a link from a mention when the message has more than one link") {
      val mentionStr = "@[nqa2](http://google.com)"
      val text = s"aaa $mentionStr bbb http://google.com ccc"
      val mention = Mention(Some(UserId()), 4, mentionStr.length)
      splitContent(text, Seq(mention), weblinkEnabled = true) shouldEqual List(
        MessageContent(TEXT, s"aaa $mentionStr bbb ", mentions = Seq(mention)),
        MessageContent(WEB_LINK, s"http://google.com"),
        MessageContent(TEXT, s" ccc")
      )
    }

    scenario("cut short a link if it starts with a mention") {
      val mentionStr = "@https://"
      val text = s"${mentionStr}google.com/"
      val mention = Mention(Some(UserId()), 0, mentionStr.length)
      splitContent(text, Seq(mention), weblinkEnabled = true) shouldEqual List(
        MessageContent(TEXT, mentionStr, mentions = Seq(mention)),
        MessageContent(WEB_LINK, "google.com/")
      )
    }

    scenario("cut short a link if it ends with a mention") {
      val mentionStr = "@nqa"
      val text = s"https://google.com/?user=${mentionStr}"
      val mention = Mention(Some(UserId()), text.indexOf(mentionStr), mentionStr.length)
      splitContent(text, Seq(mention), weblinkEnabled = true) shouldEqual List(
        MessageContent(WEB_LINK, "https://google.com/?user="),
        MessageContent(TEXT, mentionStr, mentions = Seq(mention))
      )
    }

  }

  //See this page for where the ranges were fetched from:
  //http://apps.timwhitlock.info/emoji/tables/unicode
  //TODO there are still some emojis missing - but there are no clean lists for the ranges of unicode characters

  feature("Emoji") {

    lazy val emojis = Source.fromInputStream(getClass.getResourceAsStream("/emojis.txt"))(Codec.UTF8).getLines().toSeq.filterNot(_.startsWith("#"))

    lazy val whitespaces = " \t\n\r".toCharArray.map(_.toString).toSeq

    scenario("Regular text") {
      splitContent("Hello") shouldEqual List(MessageContent(TEXT, "Hello"))
    }

    scenario("Regular text containing an emoji") {
      splitContent("Hello \uD83D\uDE01") shouldEqual List(MessageContent(TEXT, "Hello \uD83D\uDE01"))
    }

    scenario("single emoji within the range Emoticons (1F600 - 1F64F)") {
      splitContent("\uD83D\uDE00") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDE00"))
      splitContent("\uD83D\uDE4F") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDE4F"))
    }

    scenario("single emoji within the range Transport and map symbols ( 1F680 - 1F6C0 )") {
      splitContent("\uD83D\uDE80") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDE80"))
      splitContent("\uD83D\uDEC0") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDEC0"))
    }

    scenario("single emoji within the range Uncategorized ( 1F300 - 1F5FF )") {
      splitContent("\uD83C\uDF00") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83C\uDF00"))
      splitContent("\uD83D\uDDFF") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDDFF"))
    }

    scenario("multiple emojis without any whitespace") {
      splitContent("\uD83D\uDE4F\uD83D\uDE00") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDE4F\uD83D\uDE00"))
    }

    scenario("flag emoji (norwegian)") {
      splitContent("\uD83C\uDDF3\uD83C\uDDF4") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83C\uDDF3\uD83C\uDDF4"))
    }

    scenario("multiple emojis with whitespace") { //TODO, check how we should preserve this whitespace
      splitContent("\uD83D\uDE4F    \uD83D\uDE00  \uD83D\uDE05") shouldEqual List(MessageContent(TEXT_EMOJI_ONLY, "\uD83D\uDE4F    \uD83D\uDE00  \uD83D\uDE05"))
    }

    scenario("check all known emojis") {
      val (_, failed) = emojis.partition(RichMediaContentParser.containsOnlyEmojis)
      failed foreach { str =>
        info(str.map(_.toInt.toHexString).mkString(", "))
      }
      failed.toVector shouldBe empty
    }

    scenario("random emoji with whitespace") {

      case class EmojiStr(str: String) {
        override def toString: String = str.map(_.toInt.toHexString).mkString(", ")
      }

      val gen = for {
        list <- Gen.listOf(Gen.frequency((2, Gen.oneOf(emojis)), (1, Gen.oneOf(whitespaces))))
      } yield EmojiStr(list.mkString("").trim)

      forAll(gen) { str =>
        if (str.str.nonEmpty)
          splitContent(str.str) shouldEqual Seq(MessageContent(TEXT_EMOJI_ONLY, str.str))
      }
    }

    scenario("escape characters shouldn't crash") {
      val url = "https://wire.com/%20%20%25stuff"
      RichMediaContentParser.parseUriWithScheme(url).getHost shouldBe "wire.com"
    }

    scenario("invalid escaped characters shouldn't crash") {
      val url = "https://wire.com/%x%z%%"
      RichMediaContentParser.parseUriWithScheme(url).getHost shouldBe "wire.com"
    }

    scenario("invalid characters in uris shouldn't crash") {
      val url = "https://wire.com/ a"
      RichMediaContentParser.parseUriWithScheme(url).isInstanceOf[URI] shouldBe true
    }
  }
}
