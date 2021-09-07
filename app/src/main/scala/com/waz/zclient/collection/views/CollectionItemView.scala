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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, View}
import android.view.View.OnClickListener
import android.webkit.URLUtil
import android.widget.{FrameLayout, ImageView, TextView}
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.bitmap.{CenterCrop, RoundedCorners}
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.FileSharingFeatureEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.wire.signals.{EventContext, EventStream, Signal, SourceSignal}
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.glide.{CustomImageViewTarget, WireGlide}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.assets.{AssetPart, FileAssetPartView}
import com.waz.zclient.messages.parts.{EphemeralPartView, WebLinkPartView}
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

trait CollectionItemView extends ViewHelper with EphemeralPartView with DerivedLogTag {
  protected lazy val civZms = inject[Signal[ZMessaging]]
  protected lazy val messageActions = inject[MessageActionsController]
  protected lazy val collectionController = inject[CollectionController]

  private lazy val userPrefs = inject[Signal[UserPreferences]]
  lazy val restricted = userPrefs.flatMap(_.preference(FileSharingFeatureEnabled).signal.map(isAllowed => !isAllowed))

  val messageData: SourceSignal[MessageData] = Signal()

  val messageAndLikesResolver = for {
    z           <- civZms
    mId         <- messageData.map(_.id)
    message     <- z.messagesStorage.signal(mId)
    msgAndLikes <- Signal.from(z.msgAndLikes.combineWithLikes(message))
  } yield msgAndLikes

  messageAndLikesResolver.disableAutowiring()

  this.onLongClick {
    messageData.currentValue.foreach(collectionController.openContextMenuForMessage ! _)
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    messageAndLikesResolver.currentValue.exists(messageActions.showDialog(_, fromCollection = true))
  }
}

trait CollectionNormalItemView extends CollectionItemView with ClickableViewPart {
  lazy val messageTime: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__time)
  lazy val messageUser: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__user_name)

  var content = Option.empty[MessageContent]

  messageData.flatMap(msg => civZms.map(_.usersStorage).flatMap(_.signal(msg.userId))).on(Threading.Ui) {
    user =>
      messageUser.setText(user.name)
      messageUser.setTextColor(AccentColor(user.accent).color)
  }

  messageData
      .map(_.time.instant)
      .map(TimeStamp(_, showWeekday = false).string)
      .onUi(messageTime.setText)

  messageAndLikesResolver.onUi { mal => set(mal, content) }

  onClicked.onUi { _ =>
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      md <- messageData.head
    } collectionController.clickedMessage ! md
  }

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    this.content = content
    this.messageData ! messageData
  }
}

class CollectionImageView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with CollectionItemView
    with DerivedLogTag {

  import CollectionImageView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  private lazy val imageView = findById[ImageView](R.id.image)
  private lazy val ephemeralIcon = findById[View](R.id.ephemeral_icon)
  private lazy val restrictedIcon = findById[View](R.id.restricted_icon)

  val onClicked = EventStream[Unit]()

  private lazy val target = new CustomImageViewTarget(imageView)

  messageAndLikesResolver.onUi(set(_, None))

  Signal.zip(messageData.map(_.assetId), ephemeralColorDrawable, restricted).onUi {
    case (Some(id: AssetId), None, false) =>
      verbose(l"Set image asset $id")
      ephemeralIcon.setVisible(false)
      restrictedIcon.setVisible(false)
      WireGlide(context)
        .load(id)
        .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(CornerRadius)).placeholder(new ColorDrawable(Color.TRANSPARENT)))
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(target)
    case (_, _, true) =>
      ephemeralIcon.setVisible(false)
      restrictedIcon.setVisible(true)
      WireGlide(context).clear(imageView)
    case (_, Some(ephemeralDrawable), _) =>
      verbose(l"Set ephemeral drawable")
      ephemeralIcon.setVisible(true)
      restrictedIcon.setVisible(false)
      WireGlide(context).clear(imageView)
      imageView.setImageDrawable(ephemeralDrawable)
    case _ =>
      verbose(l"Set nothing")
      WireGlide(context).clear(imageView)
  }

  this.onClick {
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      md <- messageData.head
      isRestricted <- restricted.head
    } {
      if (!isRestricted) {
        collectionController.clickedMessage ! md
        onClicked ! (())
      }
    }
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    imageView.setWidth(width)
    imageView.setHeight(width)
    this.messageData ! messageData
  }
}

object CollectionImageView {
  val CornerRadius = 10
}

class CollectionWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends WebLinkPartView(context, attrs, style) with CollectionNormalItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def inflate() = inflate(R.layout.collection_message_part_weblink_content)
}

class CollectionFileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FileAssetPartView(context, attrs, style) with CollectionNormalItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def layoutList: PartialFunction[AssetPart, Int] = {
    case _: CollectionFileAssetPartView => R.layout.collection_message_file_asset_content
  }

  this.onClick {
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      false <- restricted.head
    } assetActionButton.callOnClick()
  }

  assetActionButton.callOnClick()

  assetActionButton.onClick(onClicked ! (()))
  setWillNotDraw(true)
}

class CollectionSimpleWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context: Context, attrs: AttributeSet, style: Int) with CollectionNormalItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val browser = inject[BrowserController]

  override val tpe: MsgPart = MsgPart.WebLink

  inflate(R.layout.collection_message_part_simple_link_content)

  lazy val urlTextView: TextView = findById(R.id.ttv__row_conversation__link_preview__url)

  val urlText =
    message.map(msg => msg.content.find(c => URLUtil.isValidUrl(c.content)).map(_.content).getOrElse(msg.contentString))

  urlText.onUi {
    urlTextView.setText
  }

  onClicked.onUi { _ =>
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      text  <- urlText.head
    } browser.openUrl(AndroidURIUtil.parse(text))
  }
  registerEphemeral(urlTextView)
}

case class CollectionItemViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext)
  extends RecyclerView.ViewHolder(view) {

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit =
    view.setMessageData(messageData, content)

  def setMessageData(messageData: MessageData): Unit =
    setMessageData(messageData, None)
}

case class CollectionImageViewHolder(view: CollectionImageView, listener: OnClickListener)
                                    (implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {
  view.onClicked.onUi { _ =>
    listener.onClick(view)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int): Unit =
    view.setMessageData(messageData, width, color)
}
