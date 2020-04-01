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
import android.graphics.Typeface
import android.util.{AttributeSet, TypedValue}
import android.view.{View, ViewGroup}
import android.widget.{ImageView, LinearLayout, TextView}
import com.bumptech.glide.request.RequestOptions
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.assets.Asset
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.conversation.ReplyView.ReplyBackgroundDrawable
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages._
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.ui.text.{GlyphTextView, LinkTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils.{getString, getStyledColor}
import com.waz.zclient.utils.Time.DateTimeStamp
import com.waz.zclient.utils.{RichTextView, RichView}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

abstract class ReplyPartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with EphemeralPartView
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private lazy val assetsController = inject[AssetsController]

  setOrientation(LinearLayout.HORIZONTAL)
  inflate(R.layout.message_reply_content_outer)

  protected val name: TextView = findById[TextView](R.id.name)
  protected val timestamp: TextView = findById[TextView](R.id.timestamp)
  private val content   = findById[ViewGroup](R.id.content)
  private val container = findById[ViewGroup](R.id.quote_container)

  val onQuoteClick: SourceStream[Unit] = EventStream[Unit]

  val quoteView = tpe match {
    case Reply(Text)       => Some(inflate(R.layout.message_reply_content_text,     addToParent = false))
    case Reply(Image)      => Some(inflate(R.layout.message_reply_content_image,    addToParent = false))
    case Reply(Location)   => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case Reply(AudioAsset) => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case Reply(VideoAsset) => Some(inflate(R.layout.message_reply_content_image, addToParent = false))
    case Reply(FileAsset)  => Some(inflate(R.layout.message_reply_content_generic, addToParent = false))
    case Reply(Unknown)    => Some(inflate(R.layout.message_reply_content_unknown, addToParent = false))
    case _ => None
  }
  quoteView.foreach(content.addView)

  container.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  container.setBackground(new ReplyBackgroundDrawable(getStyledColor(R.attr.replyBorderColor), getStyledColor(R.attr.wireBackgroundCollection)))

  protected val quotedMessage: SourceSignal[MessageData] with NoAutowiring = Signal[MessageData]()
  protected val quotedAsset: Signal[Option[Asset]] =
    quotedMessage.map(_.assetId).flatMap(assetsController.assetSignal).map {
      case Some(x: Asset) => Some(x)
      case _ => None
    }

  def setQuote(quotedMessage: MessageData): Unit = {
    verbose(l"setQuote: $quotedMessage")
    this.quotedMessage ! quotedMessage
  }

  private val quoteComposer =
    quotedMessage
      .map(_.userId)
      .flatMap(inject[UsersController].userOpt)

  quoteComposer
    .map { _
      .map(u => if (u.isWireBot) u.name else u.name)
      .getOrElse(Name.Empty)
    }
    .onUi(name.setText(_))

  quotedMessage
    .map(_.time.instant)
    .map(getTimeStamp)
    .onUi(timestamp.setText)

  quotedMessage.map(_.isEdited).onUi { edited =>
    name.setEndCompoundDrawable(if (edited) Some(WireStyleKit.drawEdit) else None)
  }

  container.onClick(onQuoteClick ! {()})

  private def getTimeStamp(instant: Instant) = {
    val timestamp = DateTimeStamp(instant)
    getString(
      if (timestamp.isSameDay) R.string.quote_timestamp_message_time
      else R.string.quote_timestamp_message_date,
      timestamp.string
    )
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)

    quotedMessage.map(_.time.instant).head.foreach(getTimeStamp)(Threading.Ui)
  }
}

class TextReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) with MentionsViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Text)

  private lazy val textView = findById[LinkTextView](R.id.text)

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular_small)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__huge)

  //TODO: Merge duplicated stuff from TextPartView
  quotedMessage.onUi { message =>
    val textSize = if (message.msgType == Message.Type.TEXT_EMOJI_ONLY) textSizeEmoji else textSizeRegular

    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

    val text = message.contentString
    val offset = 0
    val mentions = message.content.flatMap(_.mentions)

    if (mentions.isEmpty) {
      textView.setTransformedText(text)
      textView.markdownQuotes()
    } else {
      val (replaced, mentionHolders) = TextPartView.replaceMentions(text, mentions, offset)

      textView.setTransformedText(replaced)
      textView.markdownQuotes()

      val updatedMentions = TextPartView.updateMentions(textView.getText.toString, mentionHolders, offset)

      val spannable = TextPartView.restoreMentionHandles(textView.getText, mentionHolders)
      addMentionSpans(
        spannable,
        updatedMentions,
        None,
        getStyledColor(R.attr.wirePrimaryTextColor)
      )
      textView.setText(spannable)
    }
  }

}

class ImageReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Image)

  private val imageView = findById[ImageView](R.id.image)

  quotedMessage.map(_.assetId).onUi {
    case Some(aid: AssetId) => WireGlide(context).load(aid).apply(new RequestOptions().centerInside()).into(imageView)
    case _ => WireGlide(context).clear(imageView)
  }
}

class LocationReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Location)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedMessage.map(_.location.map(_.getName).getOrElse("")).onUi(textView.setText)
  textView.setStartCompoundDrawable(Some(WireStyleKit.drawLocation))
}

class FileReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(FileAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedAsset.map(_.map(_.name).getOrElse("")).onUi(textView.setText)
  textView.setStartCompoundDrawable(Some(WireStyleKit.drawFile))
}

class VideoReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(VideoAsset)

  private val imageView = findById[ImageView](R.id.image)
  private val imageIcon = findById[GlyphTextView](R.id.image_icon)

  quotedAsset.map(_.flatMap(_.preview)).onUi {
    case Some(aid: AssetId) => WireGlide(context).load(aid).apply(new RequestOptions().centerInside()).into(imageView)
    case _ => WireGlide(context).clear(imageView)
  }

  imageIcon.setVisibility(View.VISIBLE)
}

class AudioReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(AudioAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  textView.setText(R.string.reply_message_type_audio)
  textView.setStartCompoundDrawable(Some(WireStyleKit.drawVoiceMemo))
}

class UnknownReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Unknown)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  name.setVisibility(View.GONE)
  timestamp.setVisibility(View.GONE)
  textView.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__regular)), Typeface.ITALIC)
}
