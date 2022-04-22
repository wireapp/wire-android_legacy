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
package com.waz.zclient.messages.parts

import java.net.URL

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.{ImageView, TextView}
import androidx.cardview.widget.CardView
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.ImageViewTarget
import com.waz.api.Message.Part
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetData, AssetId, MessageContent}
import com.waz.service.messages.MessageAndLikes
import com.waz.sync.client.OpenGraphClient.{OpenGraphData, OpenGraphImage}
import com.wire.signals.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.ProgressDotsDrawable
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

class WebLinkPartView(context: Context, attrs: AttributeSet, style: Int)
  extends CardView(context, attrs, style)
    with ClickableViewPart
    with ViewHelper
    with EphemeralPartView
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.WebLink

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    part.foreach(content ! _)
  }

  private lazy val titleTextView: TextView  = findById(R.id.ttv__row_conversation__link_preview__title)
  private lazy val urlTextView: TextView    = findById(R.id.ttv__row_conversation__link_preview__url)
  private lazy val imageView: ImageView     = findById(R.id.iv__row_conversation__link_preview__image)

  private val content = Signal[MessageContent]()

  def inflate(): Unit = inflate(R.layout.message_part_weblink_content)
  inflate()

  private val linkPreview = for {
    msg <- message
    ct  <- content
  } yield {
    val index = msg.content.indexOf(ct)
    val linkIndex = msg.content.take(index).count(_.tpe == Part.Type.WEB_LINK)
    if (index >= 0 && linkIndex == 0) msg.unpackLinks else None
  }

  private val imageRequest = for {
    ct         <- content
    lp         <- linkPreview
    assetOrUrl =  (ct.openGraph, lp) match {
                    case (_, Some((_, _, Some(asset))))                                  => Some(Left(asset))
                    case (Some(OpenGraphData(_, _, Some(OpenGraphImage(url)), _, _)), _) => Some(Right(url))
                    case _                                                               => Option.empty[Either[AssetData, URL]]
                  }
    request    =  assetOrUrl.flatMap {
                    case Left(asset) => asset.remoteId.map(id => WireGlide(context).load(AssetId(id.str)))
                    case Right(uri) => Some(WireGlide(context).load(uri.toString))
                  }
  } yield request

  private val dotsDrawable = new ProgressDotsDrawable

  imageRequest.onUi {
    case Some(request) =>
      request.apply(new RequestOptions().centerCrop().placeholder(dotsDrawable))
        .into(new ImageViewTarget[Drawable](imageView) {
        override def setResource(resource: Drawable): Unit =
          registerEphemeral(imageView, resource)
      })
    case _ =>
      WireGlide(context).clear(imageView)
      imageView.setImageDrawable(dotsDrawable)

  }

  registerEphemeral(titleTextView)
  registerEphemeral(urlTextView)

  imageView.setBackground(dotsDrawable)

  private val openGraph = content.zip(linkPreview).map {
    case (_, Some((title, summary, _))) => OpenGraphData(title, summary, None, "", None)
    case (ct, _)                        => ct.openGraph.getOrElse(OpenGraphData.Empty)
  }

  openGraph.map(_.title).onUi { titleTextView.setText }
  imageRequest.map(_.isDefined).onUi { imageView.setVisible }
  content.map(c => StringUtils.trimLinkPreviewUrls(c.contentAsUri)).onUi { urlTextView.setText }

  onClicked.foreach { _ =>
    if (expired.currentValue.forall(_ == false)) {
      content.currentValue foreach { c => inject[BrowserController].openUrl(c.contentAsUri) }
    }
  }
}
