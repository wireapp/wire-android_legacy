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

import android.content.Context
import android.text.Spannable
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{AssetData, MessageData}
import com.waz.utils.events.{NoAutowiring, Signal, SourceSignal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages.{ClickableViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.text.{GlyphTextView, LinkTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils.{getString, getStyledColor}
import com.waz.zclient.utils.ZTimeFormatter
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.DateTimeUtils

abstract class ReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ClickableViewPart with ViewHelper with EphemeralPartView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private lazy val assetsController = inject[AssetsController]

  setOrientation(LinearLayout.HORIZONTAL)
  inflate(R.layout.message_reply_content_outer)

  private val name      = findById[TextView](R.id.name)
  private val timestamp = findById[TextView](R.id.timestamp)
  private val content   = findById[ViewGroup](R.id.content)

  val quoteView = tpe match {
    case Reply(Text)       => Some(inflate(R.layout.message_reply_content_text,     addToParent = false))
    case Reply(Image)      => Some(inflate(R.layout.message_reply_content_image,    addToParent = false))
    case Reply(Location)   => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case Reply(AudioAsset) => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case Reply(VideoAsset) => Some(inflate(R.layout.message_reply_content_image, addToParent = false))
    case Reply(FileAsset)  => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case _ => None
  }
  quoteView.foreach(content.addView)

  protected val quotedMessage: SourceSignal[MessageData] with NoAutowiring = Signal[MessageData]()
  protected val quotedAsset: Signal[Option[AssetData]] =
    quotedMessage.map(_.assetId).flatMap(assetsController.assetSignal).collect {
      case (asset, _) => Option(asset)
    }.orElse(Signal.const(Option.empty[AssetData]))

  def setQuote(quotedMessage: MessageData) = {
    ZLog.verbose(s"setQuote: $quotedMessage")
    this.quotedMessage ! quotedMessage
  }

  private val quoteComposer =
    quotedMessage
      .map(_.userId)
      .flatMap(inject[UsersController].userOpt)

  quoteComposer
    .map { _
      .map(u => if (u.isWireBot) u.name else u.getDisplayName)
      .getOrElse("")
    }
    .onUi(name.setText)

  quotedMessage
    .map(_.time.instant)
    .map(DateTimeUtils.toDate)
    .map(ZTimeFormatter.getSingleMessageTime(getContext, _))
    .map(getString(R.string.quote_timestamp_message, _))
    .onUi(timestamp.setText)
}

class TextReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) with MentionsViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Text)

  private lazy val textView = findById[LinkTextView](R.id.text)

  //TODO: Merge duplicated stuff from TextPartView
  quotedMessage.onUi { message =>
    textView.setText(message.contentString)
    textView.markdown()
    textView.getText match {
      case s: Spannable =>
        addMentionSpans(s, message.mentions, None, getStyledColor(R.attr.wirePrimaryTextColor))
      case _ =>
    }

    val text = message.contentString
    val offset = 0
    val mentions = message.content.flatMap(_.mentions)

    if (mentions.isEmpty) {
      textView.setText(text)
      textView.markdown()
    } else {
      val (replaced, mentionHolders) = TextPartView.replaceMentions(text, mentions, offset)

      textView.setTransformedText(replaced)
      textView.markdown()

      val updatedMentions = TextPartView.updateMentions(textView.getText.toString, mentionHolders, offset)

      textView.setText(TextPartView.restoreMentionHandles(textView.getText.toString, mentionHolders))

      textView.getText match {
        case spannable: Spannable =>
          addMentionSpans(
            spannable,
            updatedMentions,
            None,
            getStyledColor(R.attr.wirePrimaryTextColor)
          )
        case _ =>
      }
    }

  }

}

class ImageReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Image)

  private val imageContainer = findById[View](R.id.image_container)

  private val imageSignal: Signal[ImageSource] = quotedMessage.map(m => WireImage(m.assetId))

  imageContainer.setBackground(new ImageAssetDrawable(imageSignal, ScaleType.StartInside, RequestBuilder.Regular))
}

class LocationReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Location)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedMessage.map(_.location.map(_.getName).getOrElse("")).onUi(textView.setText)
}

class FileReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(FileAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedAsset.map(_.flatMap(_.name).getOrElse("")).onUi(textView.setText)
}

class VideoReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(VideoAsset)

  private val imageContainer = findById[View](R.id.image_container)
  private val imageIcon = findById[GlyphTextView](R.id.image_icon)

  private val imageSignal: Signal[ImageSource] = quotedAsset.map(_.flatMap(_.previewId)).collect {
    case Some(aId) => WireImage(aId)
  }

  imageContainer.setBackground(new ImageAssetDrawable(imageSignal, ScaleType.StartInside, RequestBuilder.Regular))
  imageIcon.setVisibility(View.VISIBLE)
}

class AudioReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(AudioAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  textView.setText(R.string.quote_audio_message)
}



trait QuoteView {
  val message = Signal[MessageData]()
}
