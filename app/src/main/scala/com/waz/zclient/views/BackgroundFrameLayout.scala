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
import android.content.res.Configuration
import android.graphics.{Color, PorterDuff, PorterDuffColorFilter, Rect}
import android.util.AttributeSet
import android.widget.FrameLayout
import com.waz.api.ImageAsset
import com.waz.model.AssetId
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.background.BackgroundObserver
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.{LayoutSpec, ViewUtils}
import com.waz.zclient.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.{R, ViewHelper}

class BackgroundFrameLayout(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper with BackgroundObserver {

  private var scaledToMax: Boolean = false

  private var width: Int = 0
  private var height: Int = 0

  private val background = Signal[ImageSource]()
  private val drawable: BlurredImageAssetDrawable = new BlurredImageAssetDrawable(background, scaleType = ScaleType.CenterCrop, blurRadius = 24, context = getContext)

  drawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.injectAlpha(0.56f, Color.BLACK), PorterDuff.Mode.DARKEN))
  setBackground(drawable)

  val isTablet = LayoutSpec.isTablet(context)
  /*if (isTablet) {
    resizeIfNeeded(getResources.getConfiguration)
  } else {
    setDrawable(ViewUtils.getOrientationDependentDisplayBounds(getContext))
  }*/


  def this(context: Context, attrs: AttributeSet) {
    this(context, attrs, 0)
  }

  def this(context: Context) {
    this(context, null)
  }

  private def setDrawable(bounds: Rect) {

  }

  def onLoadImageAsset(imageAsset: ImageAsset) {
    background ! WireImage(AssetId(imageAsset.getId))
  }

  def onScaleToMax(max: Boolean) {
    scaledToMax = max
    resizeIfNeeded(getResources.getConfiguration)
  }

  private def resizeIfNeeded(configuration: Configuration) {
    if (!isTablet) {
      return
    }
    val width: Int = if (scaledToMax) ViewUtils.toPx(getContext, configuration.screenWidthDp)
    else getResources.getDimensionPixelSize(R.dimen.framework__sidebar_width)
    val height: Int = ViewUtils.toPx(getContext, configuration.screenHeightDp)
    if (this.width != width || this.height != height) {
      resize(width, height)
    }
  }

  private def resize(width: Int, height: Int) {
    this.width = width
    this.height = height
    setDrawable(new Rect(0, 0, width, height))
  }

  def isExpanded: Boolean = {
    return scaledToMax
  }
}
