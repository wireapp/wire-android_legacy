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
import android.view.View
import android.widget.FrameLayout
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.controllers.AssetsController
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod._
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.messages.parts.assets.ImageLayoutAssetPart
import com.waz.zclient.utils.RichView

class ImagePartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ImageLayoutAssetPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  override lazy val contentLayoutId = {
    setId(R.id.content) //ensure outer view has content id set - this doesn't seem to work with merge tags
    R.layout.message_image_content
  }

  private val selection = inject[SelectionController].messages

  private lazy val assets = inject[AssetsController]
  private val imageActions = findById[View](R.id.image_actions)

  padding.on(Threading.Ui)(imageActions.setMargin)

  findById[View](R.id.button_sketch).onClick(openDrawingFragment(DRAW))

  findById[View](R.id.button_emoji).onClick(openDrawingFragment(EMOJI))

  findById[View](R.id.button_text).onClick(openDrawingFragment(TEXT))

  findById[View](R.id.button_fullscreen).onClick(message.currentValue foreach (assets.showSingleImage(_, this)))

  message.flatMap(m => selection.focused.map(_.contains(m.id))).on(Threading.Ui)(imageActions.setVisible)

  private def openDrawingFragment(drawingMethod: DrawingMethod) =
    message.currentValue foreach (assets.openDrawingFragment(_, drawingMethod))

  setBackground(imageDrawable)

}


