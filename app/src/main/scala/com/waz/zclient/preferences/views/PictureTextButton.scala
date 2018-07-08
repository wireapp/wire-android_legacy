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
package com.waz.zclient.preferences.views

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.View.{OnClickListener, OnLongClickListener}
import android.widget.{ImageView, RelativeLayout}
import com.waz.utils.events.EventStream
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.getDrawable
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class PictureTextButton(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(layoutId)

  val title     = Option(findById[TypefaceTextView](R.id.preference_title))
  val subtitle  = Option(findById[TypefaceTextView](R.id.preference_subtitle))
  val iconStart = Option(findById[ImageView](R.id.preference_icon_start))
  val iconEnd   = Option(findById[ImageView](R.id.preference_icon_end))

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.TextButton, 0, 0)

  val titleAttr         = Option(attributesArray.getString(R.styleable.TextButton_title))
  val subtitleAttr      = Option(attributesArray.getString(R.styleable.TextButton_subtitle))
  val drawableStartAttr = Option(attributesArray.getDrawable(R.styleable.TextButton_iconStart))
  val drawableEndAttr   = Option(attributesArray.getDrawable(R.styleable.TextButton_iconEnd))

  title.foreach(title => titleAttr.foreach(title.setText))
  subtitle.foreach(subtitle => setOptionText(subtitle, subtitleAttr))
  iconStart.foreach(iconStart => setOptionDrawable(iconStart, drawableStartAttr))
  iconEnd.foreach(iconEnd => setOptionDrawable(iconEnd, drawableEndAttr))

  setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = onClickEvent ! v
  })

  setOnLongClickListener(new OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      onLongClickEvent ! v
      true
    }
  })

  def layoutId = R.layout.preference_picture_text_button

  setBackground(getDrawable(R.drawable.selector__transparent_button))

  def setTitle(text: String): Unit =
    title.foreach(_.setText(text))

  def setSubtitle(text: String): Unit =
    subtitle.foreach(subtitle => setOptionText(subtitle, Some(text)))

  def setDrawableStart(drawable: Option[Drawable]): Unit =
    iconStart.foreach(iconStart => setOptionDrawable(iconStart, drawable))

  def setDrawableEnd(drawable: Option[Drawable]): Unit =
    iconEnd.foreach(iconStart => setOptionDrawable(iconStart, drawable))

  protected def setOptionDrawable(imageView: ImageView, drawable: Option[Drawable]): Unit = {
    drawable.fold {
      imageView.setVisible(false)
    }{ d =>
      imageView.setVisible(true)
      imageView.setImageDrawable(d)
    }
  }
  protected def setOptionText(textView: TypefaceTextView, text:Option[String]): Unit = {
    text.collect{case str if str.nonEmpty => str}.fold {
      textView.setVisible(false)
    }{ t =>
      textView.setVisible(true)
      textView.setText(t)
    }
  }
}
