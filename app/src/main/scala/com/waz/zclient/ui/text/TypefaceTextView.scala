/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

package com.waz.zclient.ui.text

import android.content.Context
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.AttributeSet
import com.waz.zclient.R
import com.waz.zclient.ui.utils.TypefaceUtils

class TypefaceTextView(context: Context, attrs: AttributeSet, defStyle: Int) extends ThemedTextView(context, attrs, defStyle) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private var transform = ""

  val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.TypefaceTextView, 0, 0)
  val font: String = a.getString(R.styleable.TypefaceTextView_w_font)
  if (!TextUtils.isEmpty(font)) setTypeface(font)
  transform = a.getString(R.styleable.TypefaceTextView_transform)
  if (!TextUtils.isEmpty(transform) && getText != null) setTransformedText(getText.toString, transform)
  a.recycle()
  setSoundEffectsEnabled(false)

  def setTypeface(font: String): Unit = setTypeface(TypefaceUtils.getTypeface(font))

  def setTransform(transform: String): Unit = this.transform = transform

  def setTransformedText(text: String, transform: String): Unit = {
    val transformer = TextTransform.get(transform)
    this.setText(transformer.transform(text))
  }

  def setTransformedText(text: String): Unit = {
    val transformer = TextTransform.get(this.transform)
    this.setText(transformer.transform(text))
  }
}
