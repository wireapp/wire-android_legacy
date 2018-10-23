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
package com.waz.zclient.messages.parts.quotes

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.widget.FrameLayout
import com.waz.model.Dim2
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.messages.parts.QuoteView
import com.waz.zclient.messages.parts.assets.ImageLayoutAssetPart
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Offset
import com.waz.zclient.{R, ViewHelper}

class ImageQuoteView (context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper with QuoteView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import ImageLayoutAssetPart._

  protected val imageDim = message.map(_.imageDimensions).collect { case Some(d) => d}
  protected val maxWidth = Signal[Int]()
  protected val maxHeight = Signal[Int](toPx(512)) //TODO do we have a fixed max height for replies?

//  inflate(R.layout.message_image_quote)

  private val imageDrawable = new ImageAssetDrawable(message map { m => WireImage(m.assetId) }, forceDownload = true)

  private lazy val imageContainer = returning(findById[FrameLayout](R.id.image_container)) {
    _.addOnLayoutChangeListener(new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit =
        maxWidth ! v.getWidth
    })
  }

  imageContainer.setBackground(imageDrawable)

  private val displaySize = for {
    maxW <- maxWidth
    maxH <- maxHeight
    Dim2(imW, imH) <- imageDim
  } yield {
    val heightToWidth = imH.toDouble / imW.toDouble

    val height = heightToWidth * maxW

    //fit image within view port height-wise (plus the little bit of buffer space), if it's height to width ratio is not too big. For super tall/thin
    //images, we leave them as is otherwise they might become too skinny to be viewed properly
    val scaleDownToHeight = maxH * (1 - scaleDownBuffer)
    val scaleDown = if (height > scaleDownToHeight && heightToWidth < scaleDownUnderRatio) scaleDownToHeight.toDouble / height.toDouble else 1D

    val scaledWidth = maxW * scaleDown

    //finally, make sure the width of the now height-adjusted image is either the full view port width, or less than
    //or equal to the centered area (taking left and right margins into consideration). This is important to get the
    //padding right in the next signal
    val finalWidth =
    if (scaledWidth <= maxW) scaledWidth
    else maxW

    val finalHeight = heightToWidth * finalWidth

    Dim2(finalWidth.toInt, finalHeight.toInt)
  }

  displaySize.onUi{ ds =>
    setLayoutParams(returning(getLayoutParams)(_.height = ds.height))
  }

  private val padding = for {
    maxW        <- maxWidth
    Dim2(dW, _) <- displaySize
  } yield
    if (dW >= maxW) Offset.Empty
    else Offset(0, 0, maxW - dW, 0)

  padding.onUi { p =>
    imageDrawable.padding ! p
  }

}

