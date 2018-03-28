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
package com.waz.zclient.utils

import android.content.Context
import android.content.res.{Configuration, Resources, TypedArray}
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.util.{AttributeSet, DisplayMetrics, TypedValue}
import android.view.WindowManager
import android.widget.Toast
import com.waz.zclient.ui.utils.ResourceUtils

object ContextUtils {

  def getColor(resId: Int)(implicit context: Context): Int = ContextCompat.getColor(context, resId)

  def getColorWithTheme(resId: Int, context: Context): Int =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) getColor(resId)(context)
    else context.getResources.getColor(resId, context.getTheme)

  def getColorStateList(resId: Int)(implicit context: Context) = ContextCompat.getColorStateList(context, resId)

  def getInt(resId: Int)(implicit context: Context) = context.getResources.getInteger(resId)

  def getString(resId: Int)(implicit context: Context): String = context.getResources.getString(resId)

  def getString(resId: Int, args: String*)(implicit context: Context): String =
    context.getResources.getString(resId, args:_*)

  def showToast(resId: Int, long: Boolean = true)(implicit context: Context): Unit =
    Toast.makeText(context, resId, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

  def showToast(msg: String)(implicit context: Context): Unit =
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

  def getStringOrEmpty(resId: Int)(implicit context: Context): String = if (resId > 0) getString(resId) else ""
  def getStringOrEmpty(resId: Int, args: String*)(implicit context: Context): String = if (resId > 0) getString(resId, args:_*) else ""

  def getQuantityString(resId: Int, quantity: Int, args: AnyRef*)(implicit context: Context): String = context.getResources.getQuantityString(resId, quantity, args:_*)

  def getDimenPx(resId: Int)(implicit context: Context) = context.getResources.getDimensionPixelSize(resId)
  def getDimen(resId: Int)(implicit context: Context) = context.getResources.getDimension(resId)

  def getDrawable(resId: Int, theme: Option[Resources#Theme] = None)(implicit context: Context): Drawable = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      DeprecationUtils.getDrawable(context, resId)
    } else
      context.getResources.getDrawable(resId, theme.orNull)
  }

  def getIntArray(resId: Int)(implicit context: Context) = context.getResources.getIntArray(resId)
  def getStringArray(resId: Int)(implicit context: Context) = context.getResources.getStringArray(resId)
  def getResEntryName(resId: Int)(implicit context: Context) = context.getResources.getResourceEntryName(resId)

  def getResourceFloat(resId: Int)(implicit context: Context) = ResourceUtils.getResourceFloat(context.getResources, resId)

  def toPx(dp: Int)(implicit context: Context) = (dp * context.getResources.getDisplayMetrics.density).toInt

  def getLocale(implicit context: Context) = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      DeprecationUtils.getDefaultLocale(context)
    } else {
      context.getResources.getConfiguration.getLocales.get(0)
    }
  }

  def withStyledAttributes[A](set: AttributeSet, @StyleableRes attrs: Array[Int])(body: TypedArray => A)(implicit context: Context) = {
    val a = context.getTheme.obtainStyledAttributes(set, attrs, 0, 0)
    try body(a) finally a.recycle()
  }

  def getStyledColor(resId: Int)(implicit context: Context): Int = {
    val typedValue  = new TypedValue
    val a  = context.obtainStyledAttributes(typedValue.data, Array[Int](resId))
    val color = a.getColor(0, 0)
    a.recycle()
    color
  }

  /**
    * @return the amount of pixels of the horizontal axis of the phone
    */
  def getOrientationDependentDisplayWidth(implicit context: Context): Int = context.getResources.getDisplayMetrics.widthPixels

  /**
    * @return everytime the amount of pixels of the (in portrait) horizontal axis of the phone
    */
  def getOrientationIndependentDisplayWidth(implicit context: Context): Int =
    if (isInPortrait) context.getResources.getDisplayMetrics.widthPixels
    else context.getResources.getDisplayMetrics.heightPixels

  /**
    * @return the amount of pixels of the vertical axis of the phone
    */
  def getOrientationDependentDisplayHeight(implicit context: Context): Int = context.getResources.getDisplayMetrics.heightPixels

  /**
    * @return everytime the amount of pixels of the (in portrait) width axis of the phone
    */
  def getOrientationIndependentDisplayHeight(implicit context: Context): Int =
    if (isInPortrait) context.getResources.getDisplayMetrics.heightPixels
    else context.getResources.getDisplayMetrics.widthPixels

  def getStatusBarHeight(implicit context: Context): Int = getDimensionPixelSize("status_bar_height")

  def getNavigationBarHeight(implicit context: Context): Int =
    getDimensionPixelSize(if (isInPortrait) "navigation_bar_height" else "navigation_bar_height_landscape")

  private def getDimensionPixelSize(name: String)(implicit context: Context): Int = {
    val resourceId = context.getResources.getIdentifier(name, "dimen", "android")
    if (resourceId > 0) context.getResources.getDimensionPixelSize(resourceId) else 0
  }

  def getRealDisplayWidth(implicit context: Context): Int = {
    val realMetrics = new DisplayMetrics
    context.getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager].getDefaultDisplay.getRealMetrics(realMetrics)
    realMetrics.widthPixels
  }

  def isInLandscape(implicit context: Context): Boolean = isInLandscape(context.getResources.getConfiguration)
  def isInLandscape(configuration: Configuration): Boolean = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  def isInPortrait(implicit context: Context): Boolean = isInPortrait(context.getResources.getConfiguration)
  def isInPortrait(configuration: Configuration): Boolean = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}
