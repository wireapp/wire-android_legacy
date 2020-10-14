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
package com.waz.zclient.pages.main

import java.net.URI

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, ImageView, TextView}
import com.bumptech.glide.request.RequestOptions
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Mime, Name}
import com.waz.service.assets.Content
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.utils.wrappers.{URI => URIWrapper}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.pages.main.profile.views.{ConfirmationMenu, ConfirmationMenuListener}
import com.waz.zclient.ui.theme.OptionsDarkTheme
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import com.wire.signals.{EventStream, Signal}

class ImagePreviewLayout(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ViewHelper
    with ConfirmationMenuListener
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)
  private lazy val convName = inject[ConversationController].currentConvName

  val sketchShouldBeVisible = Signal(true)
  val titleShouldBeVisible = Signal(true)

  private val onDrawClicked = EventStream[IDrawingController.DrawingMethod]()

  private var imageInput = Option.empty[Content]

  private lazy val approveImageSelectionMenu = returning(findViewById[ConfirmationMenu](R.id.cm__cursor_preview)) { menu =>
    menu.setWireTheme(new OptionsDarkTheme(getContext))
    menu.setCancel(getResources.getString(R.string.confirmation_menu__cancel))
    menu.setConfirm(getResources.getString(R.string.confirmation_menu__confirm_done))
    menu.setConfirmationMenuListener(this)
    accentColor.onUi(menu.setAccentColor)
  }

  private lazy val imageView = returning(findViewById[ImageView](R.id.iv__conversation__preview)) { view =>
    view.onClick {
      if (sketchShouldBeVisible.currentValue.getOrElse(true)) sketchMenuContainer.fade(!approveImageSelectionMenu.isVisible)
      approveImageSelectionMenu.fade(!approveImageSelectionMenu.isVisible)
      if (!TextUtils.isEmpty(titleTextView.getText)) titleTextViewContainer.fade(!approveImageSelectionMenu.isVisible)
    }
  }

  private lazy val titleTextViewContainer = returning(findViewById[FrameLayout](R.id.ttv__image_preview__title__container)) { container =>
    convName.map(TextUtils.isEmpty(_)).onUi(empty => container.setVisible(!empty))
  }

  private lazy val titleTextView = returning(findViewById[TextView](R.id.ttv__image_preview__title)) { view =>
    (for {
      visible <- titleShouldBeVisible
      name    <- if (visible) convName else Signal.const(Name.Empty)
    } yield name).onUi(view.setText(_))
  }

  private lazy val sketchMenuContainer = returning(findViewById[View](R.id.ll__preview__sketch)) { container =>
    sketchShouldBeVisible.onUi { show => container.setVisible(show) }
  }

  private lazy val sketchDrawButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__sketch)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.DRAW)
  }

  private lazy val sketchEmojiButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__emoji)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.EMOJI)
  }

  private lazy val sketchTextButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__text)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.TEXT)
  }

  override protected def onFinishInflate(): Unit = {
    super.onFinishInflate()

    // eats the click
    this.onClick({})

    imageView
    approveImageSelectionMenu
    sketchMenuContainer
    sketchDrawButton
    sketchEmojiButton
    sketchTextButton
    titleTextView
    titleTextViewContainer
  }

  onDrawClicked.onUi { method =>
    (imageInput, callback) match {
      case (Some(input), Some(c)) => c.onSketchOnPreviewPicture(input, method)
      case _ =>
    }
  }

  override def confirm(): Unit = (imageInput, callback) match {
    case (Some(input), Some(c)) => c.onSendPictureFromPreview(input)
    case _ =>
  }

  override def cancel(): Unit = {
    callback.foreach(_.onCancelPreview())
  }

  def setImage(imageData: Array[Byte]): Unit = {
    this.imageInput = Some(Content.Bytes(Mime.Image.Jpg, imageData))
    val request = WireGlide(context).load(imageData)
    request.into(imageView)
  }

  def setImage(uri: URIWrapper): Unit = {
    this.imageInput = Some(Content.Uri(URI.create(uri.toString)))

    WireGlide(context).load(URIWrapper.unwrap(uri))
      .apply(new RequestOptions().centerInside()).into(imageView)
  }

  // TODO: switch to signals after rewriting CameraFragment
  private var callback = Option.empty[ImagePreviewCallback]

  private def setCallback(callback: ImagePreviewCallback) = { this.callback = Option(callback) }

  def showSketch(show: Boolean): Unit = sketchShouldBeVisible ! show

  def showTitle(show: Boolean): Unit = titleShouldBeVisible ! show
}

trait ImagePreviewCallback {
  def onCancelPreview(): Unit

  def onSketchOnPreviewPicture(image: Content, method: IDrawingController.DrawingMethod): Unit

  def onSendPictureFromPreview(image: Content): Unit
}

object ImagePreviewLayout {

  def newInstance(context: Context, container: ViewGroup, callback: ImagePreviewCallback): ImagePreviewLayout =
    returning(LayoutInflater.from(context).inflate(R.layout.fragment_cursor_images_preview, container, false).asInstanceOf[ImagePreviewLayout]) {
      _.setCallback(callback)
    }

}
