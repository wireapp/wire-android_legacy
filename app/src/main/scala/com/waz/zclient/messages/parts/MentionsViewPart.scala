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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics._
import android.text.{Spannable, Spanned, TextPaint}
import android.text.style._
import android.view.View
import android.widget.TextView
import com.waz.zclient.R
import com.waz.zclient.messages.MessageViewPart
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils._

import scala.util.matching.Regex

trait MentionsViewPart extends MessageViewPart {

  private implicit val cxt: Context = getContext

  def addMentionSpans(textView: TextView): Unit = {
    val text = textView.getText.toString
    //TODO: use actual mentions from the message data
    val stubMentionRegex: Regex = """(\s|^)(@[\S]+)""".r
    textView.getText match {
      case spannable: Spannable =>
        stubMentionRegex.findAllMatchIn(text).foreach { m =>
          val start = m.start(2)
          val end = m.end(2)

          //TODO: check if it's self
          if (true) {
            spannable.setSpan(
              new ForegroundColorSpan(getColor(R.color.accent_blue)),
              start,
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            spannable.setSpan(
              new StyleSpan(Typeface.BOLD),
              Math.min(start + 1, end),
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            spannable.setSpan(
              new StyleSpan(Typeface.BOLD),
              Math.min(start + 1, end),
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            spannable.setSpan(
              new RelativeSizeSpan(0.9f),
              start,
              Math.min(start + 1, end),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            spannable.setSpan(
              new ClickableSpan {
                override def onClick(widget: View): Unit = {
                  //TODO: Open participant view
                }
                override def updateDrawState(ds: TextPaint): Unit = ds.setColor(ds.linkColor)
              },
              start,
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

          } else {
            spannable.setSpan(
              new SelfMentionBackgroundSpan(getColor(R.color.accent_blue), Color.WHITE, textView.getLineHeight),
              start,
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            spannable.setSpan(
              new StyleSpan(Typeface.BOLD),
              start,
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          }
        }
      case _ =>
    }
  }

  class SelfMentionBackgroundSpan(color: Int, foregroundColor: Int, textHeight: Int) extends ReplacementSpan {
    private val sidePadding = textHeight * 0.1f

    override def draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint): Unit = {
      val t = (bottom - top) / 2 - textHeight / 2
      val rect = new RectF(x, top + t, x + getSize(paint, text, start, end, paint.getFontMetricsInt), bottom - t)
      paint.setColor(ColorUtils.injectAlpha(0.5f, color))
      canvas.drawRoundRect(rect, 5f, 5f, paint)
      paint.setColor(foregroundColor)
      canvas.drawText(text, start, end, x + sidePadding, y, paint)
    }

    override def getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt): Int =
      (paint.measureText(text, start, end) + sidePadding * 2).toInt
  }

}
