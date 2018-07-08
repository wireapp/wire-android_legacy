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
import android.util.AttributeSet
import android.view.View
import android.view.View.{OnClickListener, OnLongClickListener}
import android.widget.RelativeLayout
import com.waz.utils.events.EventStream
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils.getDrawable
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class TextButton(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(layoutId)

  val title     = Option(findById[TypefaceTextView](R.id.preference_title))
  val subtitle  = Option(findById[TypefaceTextView](R.id.preference_subtitle))
  val iconStart = Option(findById[GlyphTextView](R.id.preference_icon_start))
  val iconEnd   = Option(findById[GlyphTextView](R.id.preference_icon_end))

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.TextButton, 0, 0)

  val titleAttr         = Option(attributesArray.getString(R.styleable.TextButton_title))
  val subtitleAttr      = Option(attributesArray.getString(R.styleable.TextButton_subtitle))
  val iconStartAttr = Option(attributesArray.getResourceId(R.styleable.TextButton_iconStart, 0))
  val iconEndAttr   = Option(attributesArray.getResourceId(R.styleable.TextButton_iconEnd, 0))

  title.foreach(title => titleAttr.foreach(title.setText))
  subtitle.foreach(subtitle => setOptionText(subtitle, subtitleAttr))
  iconStart.foreach(iconStart => setOptionGlyphId(iconStart, iconStartAttr))
  iconEnd.foreach(iconEnd => setOptionGlyphId(iconEnd, iconEndAttr))

  setBackground(getDrawable(R.drawable.selector__transparent_button))

  setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = onClickEvent ! v
  })

  setOnLongClickListener(new OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      onLongClickEvent ! v
      true
    }
  })

  def layoutId = R.layout.preference_text_button

  def setTitle(text: String): Unit =
    title.foreach(_.setText(text))

  def setSubtitle(text: String): Unit =
    subtitle.foreach(subtitle => setOptionText(subtitle, Some(text)))

  def setIconStart(drawable: Option[Int]): Unit =
    iconStart.foreach(iconStart => setOptionGlyphId(iconStart, drawable))

  def setIconEnd(drawable: Option[Int]): Unit =
    iconEnd.foreach(iconStart => setOptionGlyphId(iconStart, drawable))

  protected def setOptionGlyphId(glyphTextView: GlyphTextView, textId: Option[Int]): Unit = {
    textId match {
      case Some(t) if t > 0 =>
        glyphTextView.setVisible(true)
        glyphTextView.setText(t)
      case _ =>
        glyphTextView.setVisible(false)
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
