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

import android.graphics.{Canvas, Paint}
import android.text._
import android.text.style._
import com.waz.model.{Mention, UserId}

import scala.util.matching.Regex

case class CursorText(text: String, mentions: Seq[Mention]) {
  def isEmpty: Boolean = mentions.isEmpty && text.trim.isEmpty
}

object CursorText {
  val Empty: CursorText = CursorText("", Nil)
}

object MentionUtils {
  val MentionRegex: Regex = """([\s\n\r\t\n]|^)(@[\S]*)([\s\n\r\t\n]|$)""".r

  def mentionMatch(text: String, selection: Int): Option[Regex.Match] =
    MentionRegex.findAllMatchIn(text).toSeq.find { m =>
      selection > m.start(2) && selection <= m.end(2)
    }

  def mentionQuery(text: String, selection: Int): Option[String] =
    mentionMatch(text, selection).map(_.group(2).substring(1))

  case class Replacement(start: Int, end: Int, text: String)

  def getMention(text: String, selectionIndex: Int, userId: UserId, name: String): Option[(Mention, Replacement)] = mentionMatch(text, selectionIndex).map { m =>
    val atName = s"@$name".replace(" ", "\u00A0")
    val mention = Mention(Some(userId), m.start(2), atName.length)
    (mention, Replacement(m.start(2), m.end(2), atName))
  }
}

case class MentionSpan(userId: UserId, text: String, color: Int, textHeight: Int) extends ReplacementSpan {
  private val sidePadding = 0

  override def draw(canvas: Canvas, t: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint): Unit = {
    paint.setColor(color)
    canvas.drawText(text, 0, text.length, x + sidePadding, y, paint)
  }

  override def getSize(paint: Paint, t: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt): Int =
    (paint.measureText(text, 0, text.length) + sidePadding * 2).toInt
}

object MentionSpan {

  val PlaceholderChar: String = "\u00A0"

  def getMentionSpans(spannable: Spannable): Seq[(MentionSpan, Int, Int)] = {
    spannable.getSpans(0, spannable.length(), classOf[MentionSpan]).map { s =>
      (s, spannable.getSpanStart(s), spannable.getSpanEnd(s))
    }
  }

  def setSpans(spannable: Spannable, spans: Map[MentionSpan, (Int, Int)]): Unit = {
    spans.foreach {
      case (span, (start, end)) if spannable.getSpanFlags(span) == 0 =>
        if (end <= spannable.length && spannable.subSequence(start, end).toString == span.text) {
          spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      case (span, (_, _)) if spannable.getSpanFlags(span) != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE =>
        val (start, end) = (spannable.getSpanStart(span), spannable.getSpanEnd(span))
        spannable.removeSpan(span)
        spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      case _ =>
    }
  }

  def getMentionSpan(spannable: Spannable, selectionStart: Int, selectionEnd: Int): Option[MentionSpan] =
    spannable.getSpans(selectionStart, selectionEnd, classOf[MentionSpan]).headOption

  def hasMentionSpan(spannable: Spannable, selectionStart: Int, selectionEnd: Int): Boolean =
    getMentionSpan(spannable, selectionStart, selectionEnd).nonEmpty
}

class MentionSpanWatcher extends SpanWatcher {

  override def onSpanChanged(text: Spannable, what: scala.Any, ostart: Int, oend: Int, nstart: Int, nend: Int): Unit =
    what match {
      case m: MentionSpan if nend - nstart > 1 =>
        text.removeSpan(m)
        text.asInstanceOf[Editable].replace(nstart, nend, "")
      case _ =>
    }

  override def onSpanAdded(text: Spannable, what: scala.Any, start: Int, end: Int): Unit = ()

  override def onSpanRemoved(text: Spannable, what: scala.Any, start: Int, end: Int): Unit = ()
}
