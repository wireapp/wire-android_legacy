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
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{AccentColor, MessageData}
import com.waz.utils.events.Signal
import com.waz.zclient.messages.MsgPart.{Image, Location, Reply, Text}
import com.waz.zclient.messages.{ClickableViewPart, MsgPart, UsersController}
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils.ZTimeFormatter
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.DateTimeUtils

abstract class ReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ClickableViewPart with ViewHelper with EphemeralPartView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setOrientation(LinearLayout.HORIZONTAL)
  inflate(R.layout.message_reply_content_outer)

  private val name      = findById[TextView](R.id.name)
  private val timestamp = findById[TextView](R.id.timestamp)
  private val content   = findById[ViewGroup](R.id.content)


  override def onFinishInflate(): Unit = {
    super.onFinishInflate()
    val quoteView = tpe match {
      case Reply(Text)     => Some(inflate(R.layout.message_reply_content_text,     addToParent = false))
      case Reply(Image)    => Some(inflate(R.layout.message_reply_content_image,    addToParent = false))
      case Reply(Location) => Some(inflate(R.layout.message_reply_content_location, addToParent = false))
      case _ => None
    }

    if (content.getChildAt(2) != null) content.removeViewAt(2)
    quoteView.foreach(content.addView(_, 2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)))
  }

  private val quotedMessage = Signal[MessageData]()

  def setQuote(quotedMessage: MessageData) = {
    ZLog.verbose(s"setQuote: $quotedMessage")
    this.quotedMessage ! quotedMessage
  }

  private val quoteComposer =
    quotedMessage
      .map(_.userId)
      .flatMap(inject[UsersController].userOpt)

  quoteComposer
    .map { _
      .map(u => if (u.isWireBot) u.name else u.getDisplayName)
      .getOrElse("")
    }
    .onUi(name.setText)

  quoteComposer
    .map { _
      .map(_.accent)
      .map(AccentColor(_).color)
      .getOrElse(getColor(R.color.accent_default))
    }
    .onUi(name.setTextColor)

  quotedMessage
    .map(_.time.instant)
    .map(DateTimeUtils.toDate)
    .map(ZTimeFormatter.getSingleMessageTime(getContext, _))
    .onUi(timestamp.setText)
}

class TextReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = Reply(Text)


}

class ImageReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = Reply(Image)
}

class LocationReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = Reply(Location)
}



trait QuoteView {
  val message = Signal[MessageData]()
}
