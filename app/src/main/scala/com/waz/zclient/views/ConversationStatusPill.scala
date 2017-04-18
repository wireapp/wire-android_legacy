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
import android.view.{View, ViewGroup}
import android.view.View.OnClickListener
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import com.waz.utils.events.EventStream
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._

class ConversationStatusPill(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  //setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, getDimenPx(R.dimen.conversation_list__status_pill__height)))
  inflate(R.layout.conv_status_pill)

  val textView = ViewUtils.getView(this, R.id.status_pill_text).asInstanceOf[TypefaceTextView]
  val glyphView = ViewUtils.getView(this, R.id.status_pill_glyph).asInstanceOf[GlyphTextView]

  val onClickEvent = EventStream[Unit]()

  setOnClickListener(new OnClickListener {
    override def onClick(v: View) = onClickEvent ! (())
  })

  def setGlyph(glyphId: Int, backgroundId: Int = R.drawable.conversation_status_pill): Unit = {
    setVisibility(View.VISIBLE)
    glyphView.setVisibility(View.VISIBLE)
    textView.setVisibility(View.INVISIBLE)
    setBackground(getDrawable(backgroundId))
    glyphView.setText(glyphId)
  }

  def setText(text: String, backgroundId: Int = R.drawable.conversation_status_pill): Unit = {
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

  def setCount(count: Int): Unit = setText(count.toString)
  def setCalling() = setText(getString(R.string.conversation_list__action_join_call), R.drawable.conversation_status_pill_green)
  def setOngoingCall() = setGlyph(R.string.glyph__call, R.drawable.conversation_status_pill_green)
  def setMissedCall() = setGlyph(R.string.glyph__end_call)
}
