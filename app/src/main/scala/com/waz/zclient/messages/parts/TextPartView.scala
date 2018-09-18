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
import android.view.View
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.{CollectionController, CollectionUtils}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.{R, ViewHelper}

class TextPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper with ClickableViewPart with EphemeralPartView with EphemeralIndicatorPartView with MentionsViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Text

  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]
  lazy val trackingService = inject[TrackingService]

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  setOrientation(LinearLayout.HORIZONTAL)
  inflate(R.layout.message_text_content)

  private val textView = findById[LinkTextView](R.id.text)

  registerEphemeral(textView)

  textView.setOnClickListener(new OnDoubleClickListener {
    override def onSingleClick(): Unit = TextPartView.this.onSingleClick()
    override def onDoubleClick(): Unit = TextPartView.this.onDoubleClick()
  })

  textView.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(v: View): Boolean =
      TextPartView.this.getParent.asInstanceOf[View].performLongClick()
  })

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
    else ColorUtils.injectAlpha(alpha, accent.color)

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
    CollectionUtils.getHighlightedSpannableString(content, ContentSearchQuery.transliterated(content), query.elements, ColorUtils.injectAlpha(0.5f, color.color))._1

  searchResultText.on(Threading.Ui) { textView.setText }

  bgColor.on(Threading.Ui) { setBackgroundColor }

  isHighlighted.on(Threading.Ui) {
    case true => animator.start()
    case false => animator.end()
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    animator.end()
    super.set(msg, part, opts)

    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)
    textView.setTextLink(part.fold(msg.message.contentString)(_.content))
    val mentions = msg.message.content.flatMap(_.mentions)
    addMentionSpans(textView, mentions, opts.flatMap(_.selfId))
    messagePart ! part
  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) =
    part.fold(msg.msgType == Message.Type.TEXT_EMOJI_ONLY)(_.tpe == Message.Part.Type.TEXT_EMOJI_ONLY)
}
