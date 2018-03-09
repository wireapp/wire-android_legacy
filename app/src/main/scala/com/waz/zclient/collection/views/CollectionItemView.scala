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
package com.waz.zclient.collection.views

import android.content.Context
import android.support.v7.widget.{CardView, RecyclerView}
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View.OnClickListener
import android.webkit.URLUtil
import android.widget.TextView
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.AccentColor
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceSignal}
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.ImageAssetDrawable.RequestBuilder
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.common.views.{ImageAssetDrawable, ProgressDotsDrawable, RoundedImageAssetDrawable}
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.WebLinkPartView
import com.waz.zclient.messages.parts.assets.FileAssetPartView
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.utils.ZTimeFormatter._
import com.waz.zclient.utils.{RichView, ViewUtils, _}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{LocalDateTime, ZoneId}

trait CollectionItemView extends ViewHelper {
  protected lazy val civZms = inject[Signal[ZMessaging]]
  protected lazy val messageActions = inject[MessageActionsController]
  protected lazy val collectionController = inject[CollectionController]

  val messageData: SourceSignal[MessageData] = Signal()

  val messageAndLikesResolver = civZms.zip(messageData).flatMap{
    case (z, md) => Signal.future(z.msgAndLikes.combineWithLikes(md))
    case _ => Signal[MessageAndLikes]()
  }
  messageAndLikesResolver.disableAutowiring()

  this.onLongClick {
    messageData.currentValue.foreach(collectionController.openContextMenuForMessage ! _)
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    messageAndLikesResolver.currentValue.exists(messageActions.showDialog(_, fromCollection = true))
  }
}

trait CollectionNormalItemView extends CollectionItemView with ClickableViewPart{
  lazy val messageTime: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__time)
  lazy val messageUser: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__user_name)

  var content = Option.empty[MessageContent]

  messageData.flatMap(msg => civZms.map(_.usersStorage).flatMap(_.signal(msg.userId))).on(Threading.Ui) {
    user =>
      messageUser.setText(user.name)
      messageUser.setTextColor(AccentColor(user.accent).getColor())
  }

  messageData.on(Threading.Ui) {
    md =>
      val timeStr = getSeparatorTime(getContext, LocalDateTime.now, DateConvertUtils.asLocalDateTime(md.time), DateFormat.is24HourFormat(getContext), ZoneId.systemDefault, true, false)
      messageTime.setText(timeStr)
  }

  messageAndLikesResolver.on(Threading.Ui) { mal => set(mal, content) }

  onClicked.on(Threading.Ui){
    _ => messageData.currentValue.foreach(collectionController.clickedMessage ! _)
  }

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    this.content = content
    this.messageData ! messageData
  }
}

class CollectionImageView(context: Context) extends AspectRatioImageView(context) with CollectionItemView {
  setId(R.id.collection_image_view)

  val onClicked = EventStream[Unit]()

  object CollectionImageView {
    val CornerRadius = 10
  }
  import CollectionImageView._

  val padding = getResources.getDimensionPixelSize(R.dimen.collections__image_padding)
  setCropToPadding(true)
  setPadding(padding, padding, padding, padding)

  val image: Signal[ImageSource] = messageData.map(md => WireImage(md.assetId))

  private val dotsDrawable = new ProgressDotsDrawable
  private val imageDrawable = new RoundedImageAssetDrawable(image, scaleType = ImageAssetDrawable.ScaleType.CenterCrop, cornerRadius = CornerRadius, request = RequestBuilder.Single)

  setBackground(dotsDrawable)
  setImageDrawable(imageDrawable)

  this.onClick{
    onClicked ! (())
  }

  onClicked{
    _ => messageData.currentValue.foreach(collectionController.clickedMessage ! _)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    setAspectRatio(1)
    this.setWidth(width)
    this.setHeight(width)
    this.messageData ! messageData
  }
}

class CollectionWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends WebLinkPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def inflate() = inflate(R.layout.collection_message_part_weblink_content)
}

class CollectionFileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FileAssetPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def layoutList = {
    case _: CollectionFileAssetPartView => R.layout.collection_message_file_asset_content
  }

  this.onClick{
    deliveryState.currentValue.foreach(assetActionButton.onClicked ! _)
  }

  assetActionButton.onClicked{ _ =>
    messageData.currentValue.foreach(collectionController.clickedMessage ! _)
  }
}

class CollectionSimpleWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context: Context, attrs: AttributeSet, style: Int) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val browser = inject[BrowserController]

  override val tpe: MsgPart = MsgPart.WebLink

  inflate(R.layout.collection_message_part_simple_link_content)

  lazy val urlTextView: TextView    = findById(R.id.ttv__row_conversation__link_preview__url)

  val urlText =
    message.map(msg => msg.content.find(c => URLUtil.isValidUrl(c.content)).map(_.content).getOrElse(msg.contentString))

  urlText.on(Threading.Ui){ urlTextView.setText }

  onClicked{ _ =>
    urlText.currentValue foreach { c => browser.openUrl(AndroidURIUtil.parse(c)) }
  }
}

case class CollectionItemViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view){

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    view.setMessageData(messageData, content)
  }

  def setMessageData(messageData: MessageData): Unit = {
    setMessageData(messageData, None)
  }
}

case class CollectionImageViewHolder(view: CollectionImageView, listener: OnClickListener)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {
  view.onClicked { _ =>
    listener.onClick(view)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    view.setMessageData(messageData, width, color)
  }
}
