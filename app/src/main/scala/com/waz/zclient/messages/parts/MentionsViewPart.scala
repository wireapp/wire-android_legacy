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

import android.graphics._
import android.text.{Spannable, Spanned, TextPaint}
import android.text.style._
import android.view.View
import android.widget.TextView
import com.waz.model.{Mention, MessageContent, UserId}
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.ViewHelper
import com.waz.zclient.messages.{MessageView, MessageViewPart}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.ZLog.verbose
import com.waz.ZLog.ImplicitTag._

trait MentionsViewPart extends MessageViewPart with ViewHelper {

  private val participantsController = inject[ParticipantsController]

  def addMentionSpans(textView: TextView, mentions: Seq[Mention], selfId: Option[UserId], color: Int): Unit =
    textView.getText match {
      case spannable: Spannable =>
        mentions.foreach(applySpanForMention(spannable, _, selfId, color, textView.getLineHeight))
      case _ =>
    }

  private def applySpanForMention(spannable: Spannable, mention: Mention, selfId: Option[UserId], accentColor: Int, lineHeight: Int): Unit = {

    val start = mention.start
    val end = mention.start + mention.length

    def applySpanForSelfMention(): Unit = {
      verbose(s"applySpanForSelfMention, spannable: ${spannable.length()}, start: $start, end: $end")

      spannable.setSpan(
        new SelfMentionBackgroundSpan(accentColor, Color.WHITE, lineHeight),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

      spannable.setSpan(
        new StyleSpan(Typeface.BOLD),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    def applySpanForOthersMention(): Unit = {
      verbose(s"applySpanForOtherMention, spannable: ${spannable.length()}, start: $start, end: $end")
      spannable.setSpan(
        new ForegroundColorSpan(accentColor),
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
            mention.userId match {
              case Some(uId) => participantsController.onShowParticipantsWithUserId ! uId
              case _ => participantsController.onShowParticipants ! None
            }
          }

          override def updateDrawState(ds: TextPaint): Unit = ds.setColor(ds.linkColor)
        },
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    if (selfId == mention.userId)
      applySpanForSelfMention()
    else
      applySpanForOthersMention()

  }


  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MessageView.MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
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
