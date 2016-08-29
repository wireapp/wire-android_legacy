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
import android.support.v7.widget.CardView
import android.util.AttributeSet
import android.view.View
import android.widget.{ImageView, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message.Part
import com.waz.model.GenericContent.LinkPreview
import com.waz.model.GenericMessage.TextMessage
import com.waz.model.{Dim2, GenericContent, MessageContent, MessageData}
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils._
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType, State}
import com.waz.zclient.views.ImageController.{ImageUri, ProtoImage}
import com.waz.zclient.{R, ViewHelper}

class WebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.WebLink

  lazy val titleTextView: TextView          = findById(R.id.ttv__row_conversation__link_preview__title)
  lazy val urlTextView: TextView            = findById(R.id.ttv__row_conversation__link_preview__url)
  lazy val previewImageView: ImageView      = findById(R.id.iv__row_conversation__link_preview__image)
  lazy val progressDotsView: View           = findById(R.id.pdv__row_conversation__link_preview__placeholder_dots)
  lazy val previewImageContainerView: View  = findById(R.id.fl__row_conversation__link_preview__image_container)

  private val message = Signal[MessageData]()
  private val content = Signal[MessageContent]()

  val linkPreview = for {
    msg <- message
    ct <- content
  } yield {
    val index = msg.content.indexOf(ct)
    val linkIndex = msg.content.take(index).count(_.tpe == Part.Type.WEB_LINK)
    msg.protos.lastOption flatMap {
      case TextMessage(_, _, previews) if index >= 0 && previews.size > linkIndex => Some(previews(linkIndex))
      case _ => None
    }
  }

  val image = for {
    ct <- content
    lp <- linkPreview
  } yield (ct.openGraph, lp) match {
    case (_, Some(LinkPreview.WithAsset(asset)))            => Some(ProtoImage(asset))
    case (Some(OpenGraphData(_, _, Some(uri), _, _)), None) => Some(ImageUri(uri))
    case _                                                  => None
  }

  val dimensions = content.zip(linkPreview) map {
    case (_, Some(LinkPreview.WithAsset(GenericContent.Asset.WithDimensions(d)))) => d
    case (ct, _) => Dim2(ct.width, ct.height)
  }

  val openGraph = content.zip(linkPreview) map {
    case (_, Some(LinkPreview.WithDescription(t, s))) => OpenGraphData(t, s, None, "", None)
    case (ct, _) => ct.openGraph.getOrElse(OpenGraphData.Empty)
  }

  val hasImage = image.map(_.isDefined)

  val title = openGraph.map(_.title)
  val urlText = content.map(c => StringUtils.trimLinkPreviewUrls(c.contentAsUri))

  private val imageDrawable = new ImageAssetDrawable(image.collect { case Some(im) => im }, scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single)

  this.onClick {
    content.currentValue foreach { c =>

      verbose(s"on link click: ${c.contentAsUri}")
    }
  }

  private lazy val bindViews = {
    previewImageView.setImageDrawable(imageDrawable)

    hasImage.on(Threading.Ui) { previewImageContainerView.setVisible }

    imageDrawable.state.map(_ == State.Loading) { progressDotsView.setVisible }

    title.on(Threading.Ui) { titleTextView.setText }

    urlText.on(Threading.Ui) { urlTextView.setText }
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    verbose(s"set $part")
    bindViews
    message ! msg
    part foreach { content ! _ }
  }
}
