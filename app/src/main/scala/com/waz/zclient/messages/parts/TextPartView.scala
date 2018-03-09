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

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Color
import android.util.{AttributeSet, TypedValue}
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.{CollectionController, CollectionUtils}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.{R, ViewHelper}

class TextPartView(context: Context, attrs: AttributeSet, style: Int) extends LinkTextView(context, attrs, style) with ViewHelper with ClickableViewPart with EphemeralPartView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Text

  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  registerEphemeral(this)

  var messagePart = Signal[Option[MessageContent]]()

  val animAlpha = Signal(0f)
  val animator = ValueAnimator.ofFloat(1, 0).setDuration(1500)
  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit =
      animAlpha ! Math.min(animation.getAnimatedValue.asInstanceOf[Float], 0.5f)
  })

  val bgColor = for {
    accent <- accentColorController.accentColor
    alpha <- animAlpha
  } yield
    if (alpha <= 0) Color.TRANSPARENT
    else ColorUtils.injectAlpha(alpha, accent.getColor())

  val isHighlighted = for {
    msg <- message
    focused <- collectionController.focusedItem
  } yield focused.exists(_.id == msg.id)

  val searchResultText = for {
    color <- accentColorController.accentColor
    query <- collectionController.contentSearchQuery if !query.isEmpty
    searchResults <- collectionController.matchingTextSearchMessages
    msg <- message if searchResults(msg.id)
    part <- messagePart
    content = part.fold(msg.contentString)(_.content)
  } yield
    CollectionUtils.getHighlightedSpannableString(content, ContentSearchQuery.transliterated(content), query.elements, ColorUtils.injectAlpha(0.5f, color.getColor()))._1

  searchResultText.on(Threading.Ui) { setText }

  bgColor.on(Threading.Ui) { setBackgroundColor }

  isHighlighted.on(Threading.Ui) {
    case true => animator.start()
    case false => animator.end()
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    animator.end()
    super.set(msg, part, opts)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)
    setTextLink(part.fold(msg.message.contentString)(_.content))
    messagePart ! part
  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) =
    part.fold(msg.msgType == Message.Type.TEXT_EMOJI_ONLY)(_.tpe == Message.Part.Type.TEXT_EMOJI_ONLY)
}
