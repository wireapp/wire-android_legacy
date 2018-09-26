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
import com.waz.model.{Mention, MessageContent, UserId}
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.messages.{MessageView, MessageViewPart}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.utils.returning
import com.waz.zclient.utils.ContextUtils

trait MentionsViewPart extends MessageViewPart with ViewHelper {

  private val participantsController = inject[ParticipantsController]

  def addMentionSpans(spannable: Spannable, mentions: Seq[Mention], selfId: Option[UserId], color: Int): Unit = {
      spannable.getSpans(0, spannable.length(), classOf[OtherMentionSpan]).foreach(spannable.removeSpan)
      spannable.getSpans(0, spannable.length(), classOf[SelfMentionBackgroundSpan]).foreach(spannable.removeSpan)

      mentions.foreach(applySpanForMention(spannable, _, selfId, color))
    }

  private def applySpanForMention(spannable: Spannable, mention: Mention, selfId: Option[UserId], accentColor: Int): Unit = {

    val start = Math.min(mention.start, spannable.length())
    val end = Math.min(mention.start + mention.length, spannable.length())

    def applySpanForSelfMention(): Unit = {
      spannable.setSpan(
        new SelfMentionBackgroundSpan(accentColor, ContextUtils.getStyledColor(R.attr.wirePrimaryTextColor)),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    def applySpanForOthersMention(): Unit = {
      spannable.setSpan(
        OtherMentionSpan(accentColor),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

      spannable.getSpans(start, end, classOf[ClickableSpan]).foreach(spannable.removeSpan)
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

  class SelfMentionBackgroundSpan(color: Int, foregroundColor: Int) extends OtherMentionSpan(foregroundColor) {

    override def draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint): Unit = {
      val textTop = y + paint.ascent()
      val textBottom = y + paint.descent()

      val rect = new RectF(x, textTop, x + getSize(paint, text, start, end, paint.getFontMetricsInt), textBottom)

      val backgroundPaint = new Paint()
      backgroundPaint.setColor(ColorUtils.injectAlpha(0.4f, color))
      canvas.drawRoundRect(rect, 5f, 5f, backgroundPaint)

      super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }
  }

  case class OtherMentionSpan(color: Int) extends ReplacementSpan {

    override def draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint): Unit = {
      val atPaint = new Paint(paint)
      atPaint.setTypeface(Typeface.DEFAULT)
      atPaint.setColor(color)
      atPaint.setTextSize(atPaint.getTextSize * 0.9f)

      val boldPaint = new Paint(paint)
      boldPaint.setTypeface(Typeface.DEFAULT_BOLD)
      boldPaint.setColor(color)

      if (text.length() > start + 1) {
        canvas.drawText(text, start, start + 1, x, y, atPaint)
        canvas.drawText(text, start + 1, end, x + atPaint.measureText(text, start, start + 1), y, boldPaint)
      }
    }

    override def getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt): Int = {
      paint.getFontMetricsInt(fm)
      returning(new Paint(paint))(_.setTypeface(Typeface.DEFAULT_BOLD)).measureText(text, start, end).toInt
    }

  }

}
