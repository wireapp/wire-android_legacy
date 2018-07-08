/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.waz.zclient.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{Gravity, View, ViewGroup}
import android.widget.FrameLayout.LayoutParams
import android.widget.LinearLayout
import com.waz.utils.events.EventStream
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.{R, ViewHelper}

class FlatWireButton(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.flat_wire_button)
  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.getResources.getDimensionPixelSize(R.dimen.flat_wire_button_height)))

  val glyph = findById[GlyphTextView](R.id.icon)
  val text = findById[TypefaceTextView](R.id.text)
  val accentColor = inject[AccentColorController].accentColor

  val onClickEvent = EventStream[View]()

  setGravity(Gravity.CENTER)
  accentColor.onUi{ accentColor =>
    setBackgroundColor(accentColor.getColor())
  }

  setOnClickListener(new OnClickListener {
    override def onClick(v: View) = onClickEvent ! v
  })

  def setText(textId: Int): Unit = text.setText(textId)
  def setGlyph(glyphId: Int): Unit = glyph.setText(glyphId)

}
