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
package com.waz.zclient.cursor

import android.graphics.Color
import android.text._
import android.text.style.ForegroundColorSpan
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.model.UserId
import com.waz.utils.events.{Signal, SourceSignal}
import Mention._
import scala.util.matching.Regex

case class Mention(start: Int, end: Int, userId: UserId)
case class CursorText(text: String, mentions: Seq[Mention]) {

  def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): CursorText = {
    val end = start + count
    val unchangedMentions = mentions.filter(m => start >= m.end || end < m.start).map { m =>
      if (m.start > end) {
        val offset = count - before
        m.copy(m.start + offset, m.end + offset)
      } else m
    }
    CursorText(s.toString, unchangedMentions)
  }

  def addMention(mention: Mention): CursorText = copy(mentions = mentions :+ mention)
}

object Mention {
  val MentionRegex: Regex = """@[\S]*$""".r

  def mentionMatch(text: String, selection: Int): Option[Regex.Match] =
    MentionRegex.findAllMatchIn(text.subSequence(0, selection)).toSeq.lastOption

  def mentionQuery(text: String, selection: Int): Option[String] =
    MentionRegex.findFirstIn(text.subSequence(0, selection)).map(_.substring(1))

  case class Replacement(start: Int, end: Int, text: String)

  def getMention(text: String, selectionIndex: Int, userId: UserId, name: String): Option[(Mention, Replacement)] = mentionMatch(text, selectionIndex).map { m =>
    val atName = s"@$name"
    val mention = Mention(m.start, m.start + atName.length, userId)
    (mention, Replacement(m.start, m.end, atName))
  }
}

class MentionsTextWatcher extends TextWatcher {
  var cursorText = CursorText("", Seq())

  val shouldShowMentionList: SourceSignal[Option[String]] = Signal()

  override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

  override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {
    cursorText = cursorText.onTextChanged(s, start, before, count)
    shouldShowMentionList ! mentionQuery(s.toString, start + count)
  }

  override def afterTextChanged(s: Editable): Unit = {
    refreshSpans(s)
  }

  private def refreshSpans(s: Editable): Unit = {
    s.getSpans(0, s.length(), classOf[ForegroundColorSpan]).foreach(s.removeSpan(_))
    cursorText.mentions.foreach { mention =>
      if (mention.end < s.length() && mention.start >= 0) {
        s.setSpan(
          new ForegroundColorSpan(Color.BLUE), //TODO: Accent color?
          mention.start,
          mention.end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      } else {
        ZLog.error(s"Incorrect mention span: $mention")
      }
    }
  }

  def createMention(userId: UserId, name: String, editable: Editable, selectionIndex: Int): Unit = {
    getMention(editable.toString, selectionIndex, userId, name).foreach {
      case (mention, Replacement(rStart, rEnd, rText)) =>
        editable.replace(rStart, rEnd, rText + " ")
        cursorText = cursorText.addMention(mention)
        refreshSpans(editable)
    }
  }
}
