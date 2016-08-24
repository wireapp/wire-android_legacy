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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.{ImageView, LinearLayout}
import com.waz.model.{AssetId, Dim2, MessageContent, MessageData}
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.controllers.ImageController
import com.waz.zclient.messages.{MessageViewPart, MsgPart}

class ImagePartView(context: Context, attrs: AttributeSet, style: Int) extends ImageView(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  val images = inject[ImageController]

  private val assetId = Signal[AssetId]()
  private val imageDim = Signal[Dim2]()
  private val width = Signal[Int]()

  val bitmap = for {
    w <- width
    id <- assetId
    res <- images.imageSignal(id, w)
  } yield res match {
    case BitmapResult.BitmapLoaded(bmp, _, _) => bmp
    case _ => null
  }

  val height = for {
    w <- width
    Dim2(imW, imH) <- imageDim
  } yield imH * w / imW  // TODO: improve image view size computation

  bitmap.on(Threading.Ui) { setImageBitmap }

  height { h =>
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit = {
    imageDim ! msg.imageDimensions.getOrElse(Dim2(1, 1))
    assetId ! msg.assetId
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    width ! (right - left)
  }
}
