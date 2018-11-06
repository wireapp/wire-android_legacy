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
package com.waz.zclient.conversation

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import android.util.{AttributeSet, TypedValue}
import android.view.{View, ViewGroup}
import android.widget._
import com.waz.api.Message.Type
import com.waz.model.{AssetData, AssetId, MessageData}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.common.views.RoundedImageAssetDrawable
import com.waz.zclient.conversation.ReplyView.ReplyBackgroundDrawable
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.paintcode.WireStyleKit.ResizingBehavior
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichTextView, RichView}
import com.waz.zclient.{R, ViewHelper}

class ReplyView(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.reply_view)

  private val closeButton = findById[ImageButton](R.id.reply_close)
  private val senderText = findById[TextView](R.id.reply_sender)
  private val contentText = findById[LinkTextView](R.id.reply_content)
  private val image = findById[ImageView](R.id.reply_image)
  private val container = findById[ViewGroup](R.id.reply_container)

  private var onClose: () => Unit = () => {}

  closeButton.onClick(onClose())

  container.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  container.setBackground(new ReplyBackgroundDrawable(getStyledColor(R.attr.replyBorderColor), getStyledColor(R.attr.wireBackgroundColor)))

  def setOnClose(onClose: => Unit): Unit = this.onClose = () => onClose

  def setMessage(messageData: MessageData, assetData: Option[AssetData], senderName: String): Unit = {
    setSender(senderName, !messageData.editTime.isEpoch)

    messageData.msgType match {
      case Type.TEXT | Type.TEXT_EMOJI_ONLY | Type.RICH_MEDIA =>
        setTextMessage(messageData.contentString)
      case Type.LOCATION =>
        setLocation(messageData.location.map(_.getName).getOrElse(getString(R.string.reply_message_type_location)))
      case Type.VIDEO_ASSET =>
        setVideoMessage(messageData.assetId)
      case Type.ASSET =>
        setImageMessage(messageData.assetId)
      case Type.AUDIO_ASSET =>
        setAudioMessage()
      case Type.ANY_ASSET =>
        setAsset(assetData.flatMap(_.name).getOrElse(getString(R.string.reply_message_type_asset)))
      case _ =>
      // Other types shouldn't be able to be replied to
    }
  }

  private def setSender(name: String, edited: Boolean): Unit = {
    senderText.setText(name)
    senderText.setEndCompoundDrawable(if (edited) Some(WireStyleKit.drawEdit) else None, getStyledColor(R.attr.wirePrimaryTextColor))
  }

  private def setImageMessage(assetId: AssetId): Unit = {
    image.setVisibility(View.VISIBLE)
    contentText.setText(R.string.reply_message_type_image)
    setBoldContent()
    setStartIcon(Some(WireStyleKit.drawImage))
    val imageDrawable = new RoundedImageAssetDrawable(Signal.const(WireImage(assetId)), scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single, cornerRadius = 10)
    image.setImageDrawable(imageDrawable)
  }

  private def setVideoMessage(assetId: AssetId): Unit = {
    image.setVisibility(View.VISIBLE)
    contentText.setText(R.string.reply_message_type_video)
    setBoldContent()
    setStartIcon(Some(WireStyleKit.drawCamera))
    val imageDrawable = new RoundedImageAssetDrawable(Signal.const(WireImage(assetId)), scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single, cornerRadius = 10)
    image.setImageDrawable(imageDrawable)
  }

  private def setTextMessage(content: String): Unit = {
    image.setVisibility(View.GONE)
    setStartIcon(None)
    contentText.setText(content)
    setRegularContent()
  }

  private def setLocation(addressText: String): Unit = {
    image.setVisibility(View.GONE)
    setStartIcon(Some(WireStyleKit.drawLocation))
    contentText.setText(addressText)
    setBoldContent()
  }

  private def setAsset(fileDescription: String): Unit = {
    image.setVisibility(View.GONE)
    setStartIcon(Some(WireStyleKit.drawFile))
    contentText.setText(fileDescription)
    setBoldContent()
  }

  private def setAudioMessage(): Unit = {
    image.setVisibility(View.GONE)
    setStartIcon(Some(WireStyleKit.drawVoiceMemo))
    contentText.setText(R.string.reply_message_type_audio)
    setBoldContent()
  }

  private def setStartIcon(drawMethod: Option[(Canvas, RectF, ResizingBehavior, Int) => Unit]): Unit = {
    contentText.setStartCompoundDrawable(drawMethod, getStyledColor(R.attr.wirePrimaryTextColor))
  }

  private def setBoldContent(): Unit = {
    contentText.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
    contentText.setAllCaps(true)
    contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__smaller))
  }


  private def setRegularContent(): Unit = {
    contentText.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__regular)))
    contentText.setAllCaps(false)
    contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__small))
    contentText.markdownQuotes()
  }
}

object ReplyView {

  class ReplyBackgroundDrawable(borderColor: Int, backgroundColor: Int) extends Drawable {

    private val paint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(borderColor))
    private val radius = 25
    private val strokeWidth = 4
    private val sideBarWidth = 15

    override def draw(canvas: Canvas): Unit = {
      val rect = new RectF(canvas.getClipBounds)
      rect.inset(strokeWidth, strokeWidth)

      paint.setXfermode(null)
      paint.setStyle(Paint.Style.FILL_AND_STROKE)
      paint.setColor(backgroundColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeWidth(strokeWidth)
      paint.setColor(borderColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
      paint.setStyle(Paint.Style.FILL)
      canvas.drawRect(0, rect.top, sideBarWidth, rect.bottom, paint)

    }

    override def setAlpha(alpha: Int): Unit = paint.setAlpha(alpha)

    override def setColorFilter(colorFilter: ColorFilter): Unit = paint.setColorFilter(colorFilter)

    override def getOpacity: Int = paint.getAlpha
  }
}
