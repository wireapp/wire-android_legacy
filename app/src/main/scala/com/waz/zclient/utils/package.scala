/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient

import android.content.Context
import android.content.res.TypedArray
import android.graphics.LightingColorFilter
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.support.v7.preference.Preference
import android.support.v7.preference.Preference.{OnPreferenceChangeListener, OnPreferenceClickListener}
import android.util.AttributeSet
import android.view.View._
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.{View, ViewGroup}
import android.widget.SeekBar
import com.waz.zclient.ui.utils.ResourceUtils
import com.waz.zclient.ui.views.OnDoubleClickListener

package object utils {

  case class Offset(l: Int, t: Int, r: Int, b: Int)
  object Offset {
    val Empty = Offset(0, 0, 0, 0)
  }

  implicit class RichView(val view: View) extends AnyVal {

    def setVisible(isVisible: Boolean): Unit = view.setVisibility(if (isVisible) VISIBLE else GONE)

    def setGone(isGone: Boolean): Unit = view.setVisibility(if (isGone) GONE else VISIBLE)

    def isVisible = view.getVisibility == VISIBLE

    def setMarginTop(m: Int) = {
      view.getLayoutParams.asInstanceOf[ViewGroup.MarginLayoutParams].topMargin = m
      view.requestLayout()
    }

    def setMargin(r: Offset): Unit = setMargin(r.l, r.t, r.r, r.b)

    def setMargin(l: Int, t: Int, r: Int, b: Int): Unit = {
      val lp = view.getLayoutParams.asInstanceOf[ViewGroup.MarginLayoutParams]
      lp.leftMargin = l
      lp.topMargin = t
      lp.rightMargin = r
      lp.bottomMargin = b
      view.requestLayout()
    }

    //TODO improve this so that multiple click listeners can be set from different places at once
    //TODO could also handle a set of views?
    def onClick(f: => Unit): Unit = view.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = f
    })

    def onClick(onSingleClickArg: => Unit, onDoubleClickArg: => Unit): Unit = view.setOnClickListener(new OnDoubleClickListener {
      override def onSingleClick(): Unit = onSingleClickArg
      override def onDoubleClick(): Unit = onDoubleClickArg
    })

    def onLongClick(f: => Boolean): Unit = view.setOnLongClickListener(new OnLongClickListener {
      override def onLongClick(v: View): Boolean = f
    })

    def setWidthAndHeight(w: Option[Int] = None, h: Option[Int] = None) = {
      view.setLayoutParams(Option(view.getLayoutParams) match {
        case Some(p) =>
          p.width = w.getOrElse(p.width)
          p.height = h.getOrElse(p.height)
          p
        case _ =>
          new LayoutParams(w.getOrElse(WRAP_CONTENT), h.getOrElse(WRAP_CONTENT))
      })
    }
    def setWidth(w: Int): Unit = setWidthAndHeight(w = Some(w))
    def setHeight(h: Int): Unit = setWidthAndHeight(h = Some(h))
  }

  implicit class RichPreference(val pref: Preference) extends AnyVal {
    def onClick(f: => Unit) = pref.setOnPreferenceClickListener(new OnPreferenceClickListener {
      override def onPreferenceClick(preference: Preference): Boolean = {
        f
        true
      }
    })

    /**
      * @param f True to update the state of the Preference with the new value.
      */
    def onChange(f: Any => Boolean) = pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener {
      override def onPreferenceChange(preference: Preference, o: Any): Boolean = {
        f(o)
        true
      }
    })
  }

  implicit class RichSeekBar(val bar: SeekBar) extends AnyVal {
    def setColor(color: Int): Unit = {
      val progressDrawable = Option(bar.getProgressDrawable).map {
        case d: LayerDrawable => Option(d.findDrawableByLayerId(android.R.id.progress)).getOrElse(d)
        case d => d
      }
      val thumbDrawable = Option(bar.getThumb)
      val filter = new LightingColorFilter(0xFF000000, color)
      Seq(progressDrawable, thumbDrawable).foreach(_.foreach(_.setColorFilter(filter)))
    }
  }

  object ContextUtils {

    def getColor(resId: Int)(implicit context: Context) = ContextCompat.getColor(context, resId)

    def getColorStateList(resId: Int)(implicit context: Context) = ContextCompat.getColorStateList(context, resId)

    def getInt(resId: Int)(implicit context: Context) = context.getResources.getInteger(resId)

    def getString(resId: Int)(implicit context: Context): String = context.getResources.getString(resId)
    def getString(resId: Int, args: String*)(implicit context: Context): String = context.getResources.getString(resId, args:_*)

    def getStringOrEmpty(resId: Int)(implicit context: Context): String = if (resId > 0) getString(resId) else ""
    def getStringOrEmpty(resId: Int, args: String*)(implicit context: Context): String = if (resId > 0) getString(resId, args:_*) else ""

    def getQuantityString(resId: Int, quantity: Int, args: AnyRef*)(implicit context: Context): String = context.getResources.getQuantityString(resId, quantity, args:_*)

    def getDimenPx(resId: Int)(implicit context: Context) = context.getResources.getDimensionPixelSize(resId)
    def getDimen(resId: Int)(implicit context: Context) = context.getResources.getDimension(resId)

    def getDrawable(resId: Int)(implicit context: Context) = {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        //noinspection ScalaDeprecation
        context.getResources.getDrawable(resId)
      } else
        context.getResources.getDrawable(resId, null)
    }

    def getIntArray(resId: Int)(implicit context: Context) = context.getResources.getIntArray(resId)
    def getResEntryName(resId: Int)(implicit context: Context) = context.getResources.getResourceEntryName(resId)

    def getResourceFloat(resId: Int)(implicit context: Context) = ResourceUtils.getResourceFloat(context.getResources, resId)

    def toPx(dp: Int)(implicit context: Context) = (dp * context.getResources.getDisplayMetrics.density).toInt

    def getLocale(implicit context: Context) = {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        //noinspection ScalaDeprecation
        context.getResources.getConfiguration.locale
      } else {
        context.getResources.getConfiguration.getLocales.get(0)
      }
    }

    def withStyledAttributes[A](set: AttributeSet, @StyleableRes attrs: Array[Int])(body: TypedArray => A)(implicit context: Context) = {
      val a = context.getTheme.obtainStyledAttributes(set, attrs, 0, 0)
      try body(a) finally a.recycle()
    }
  }
}
