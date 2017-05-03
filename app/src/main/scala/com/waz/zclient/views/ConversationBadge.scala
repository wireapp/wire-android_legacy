/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import com.waz.utils.events.EventStream
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.ConversationBadge._
import com.waz.zclient.{R, ViewHelper}

object ConversationBadge {
  trait Status
  case object Muted extends Status
  case object Empty extends Status
  case object WaitingConnection extends Status
  case object Ping extends Status
  case object Typing extends Status
  case object OngoingCall extends Status
  case object IncomingCall extends Status
  case object MissedCall extends Status
  case class Count(count: Int) extends Status
}

class ConversationBadge(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.conv_badge)

  val textView = ViewUtils.getView(this, R.id.status_pill_text).asInstanceOf[TypefaceTextView]
  val glyphView = ViewUtils.getView(this, R.id.status_pill_glyph).asInstanceOf[GlyphTextView]

  val onClickEvent = EventStream[Status]()

  var status: Status = Empty

  setOnClickListener(new OnClickListener {
    override def onClick(v: View) = onClickEvent ! status
  })

  def setGlyph(glyphId: Int, backgroundId: Int = R.drawable.conversation_badge): Unit = {
    setVisibility(View.VISIBLE)
    glyphView.setVisibility(View.VISIBLE)
    textView.setVisibility(View.GONE)
    setBackground(getDrawable(backgroundId))
    glyphView.setText(glyphId)
  }

  def setText(text: String, backgroundId: Int = R.drawable.conversation_badge): Unit = {
    setVisibility(View.VISIBLE)
    textView.setVisibility(View.VISIBLE)
    glyphView.setVisibility(View.INVISIBLE)
    setBackground(getDrawable(backgroundId))
    textView.setText(text)
  }

  def setHidden() = setVisibility(View.INVISIBLE)

  def setMuted(): Unit = setGlyph(R.string.glyph__silence)
  def setWaitingForConnection(): Unit = setGlyph(R.string.glyph__clock)
  def setPing(): Unit = setGlyph(R.string.glyph__ping)
  def setTyping(): Unit = setGlyph(R.string.glyph__edit)

  def setCount(count: Int): Unit = setText(count.toString)
  def setIncomingCalling() = setText(getString(R.string.conversation_list__action_join_call), R.drawable.conversation_badge_green)
  def setOngoingCall() = setGlyph(R.string.glyph__call, R.drawable.conversation_badge_green)
  def setMissedCall() = setGlyph(R.string.glyph__end_call)

  def setStatus(status: Status): Unit = {
    this.status = status
    status match {
      case Typing =>
        setTyping()
      case Muted =>
        setMuted()
      case WaitingConnection =>
        setWaitingForConnection()
      case Ping =>
        setPing()
      case OngoingCall =>
        setOngoingCall()
      case IncomingCall =>
        setIncomingCalling()
      case MissedCall =>
        setMissedCall()
      case Count(count) =>
        setCount(count)
      case Empty =>
        setHidden()
    }
  }
}
