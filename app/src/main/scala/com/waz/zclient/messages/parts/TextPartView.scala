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

import java.util.UUID

import android.content.Context
import android.graphics.Color
import android.text.{Spannable, SpannableString, SpannableStringBuilder}
import android.util.{AttributeSet, TypedValue}
import android.view.View
import android.widget.LinearLayout
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.model.{Mention, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.wire.signals.Signal
import com.waz.zclient.collection.controllers.{CollectionController, CollectionUtils}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, HighlightViewPart, MsgPart}
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.{BuildConfig, R, ViewHelper}
import com.waz.threading.Threading._
import com.waz.utils.WhereAmI

class TextPartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper with ClickableViewPart with EphemeralPartView
    with EphemeralIndicatorPartView with MentionsViewPart with HighlightViewPart {

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

  val messagePart = Signal[Option[MessageContent]]()

  val searchResultText = for {
    color         <- accentColorController.accentColor
    query         <- collectionController.contentSearchQuery if !query.isEmpty
    searchResults <- collectionController.matchingTextSearchMessages
    msg           <- message if searchResults(msg.id)
    part          <- messagePart
    content       =  part.fold(msg.contentString)(_.content)
  } yield
    CollectionUtils.getHighlightedSpannableString(content, ContentSearchQuery.transliterated(content), query.elements, ColorUtils.injectAlpha(0.5f, color.color))._1

  searchResultText.onUi { textView.setText }

  private def setText(text: String): Unit = { // TODO: remove try/catch blocks when the bug is fixed
    try {
      textView.setTransformedText(text)
    } catch {
      case ex: Exception =>
        warn(l"""
          Error while transforming text link,
          exception type: ${ex.getClass.getCanonicalName},
          text: ${redactedString(text)},
          stacktrace: ${WhereAmI.whereAmI(ex)}
        """)
        if (BuildConfig.FLAVOR == "internal") throw ex
    }

    try {
      textView.markdown()
    } catch {
      case ex: Exception =>
        warn(l"""
          Error on markdown,
          exception type: ${ex.getClass.getCanonicalName},
          text: ${redactedString(text)},
          stacktrace: ${WhereAmI.whereAmI(ex)}
        """)
        if (BuildConfig.FLAVOR == "internal") throw ex
    }
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    //animator.end()
    stopHighlight()
    super.set(msg, part, opts)

    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)

    val contentString = msg.message.contentString
    val (text, offset) = part.fold(contentString, 0)(ct => (ct.content, contentString.indexOf(ct.content)))
    val mentions = msg.message.content.flatMap(_.mentions)

    if (mentions.isEmpty) setText(text)
    else {
      // https://github.com/wearezeta/documentation/blob/master/topics/mentions/use-cases/002-receive-and-display-message.md#step-2-replace-mention-in-message
      val (replaced, mentionHolders) = TextPartView.replaceMentions(text, mentions, offset)

      setText(replaced)

      val updatedMentions = TextPartView.updateMentions(textView.getText.toString, mentionHolders, offset)

      val spannable = TextPartView.restoreMentionHandles(textView.getText, mentionHolders)
      addMentionSpans(
        spannable,
        updatedMentions,
        opts.flatMap(_.selfId),
        accentColorController.accentColor.map(_.color).currentValue.getOrElse(Color.BLUE)
      )
      textView.setText(spannable)
    }

    textView.setTextLink()

    messagePart ! part
  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) =
    part.fold(msg.msgType == Message.Type.TEXT_EMOJI_ONLY)(_.tpe == Message.Part.Type.TEXT_EMOJI_ONLY)

}

object TextPartView {
  case class MentionHolder(mention: Mention, uuid: String, handle: String)

  def replaceMentions(text: String, mentions: Seq[Mention], offset: Int = 0): (String, Seq[MentionHolder]) = {
    val (accStr, mentionHolders, resultIndex) =
      mentions.sortBy(_.start).foldLeft(("", Seq.empty[MentionHolder], 0)){
        case ((accStr, acc, resultIndex), mention) =>
          val start = mention.start - offset
          val end   = start + mention.length
          val uuid  = UUID.randomUUID().toString
          (
            accStr + text.substring(resultIndex, start) + uuid,
            acc ++ Seq(MentionHolder(mention, uuid, text.substring(start, end))),
            end
          )
    }

    (
      if (resultIndex < text.length) accStr + text.substring(resultIndex) else accStr,
      mentionHolders
    )
  }

  def updateMentions(text: String, mentionHolders: Seq[MentionHolder], offset: Int = 0): Seq[Mention] =
    mentionHolders.sortBy(_.mention.start).foldLeft((text, Seq.empty[Mention])) {
      case ((oldText, acc), holder) if oldText.contains(holder.uuid) =>
        val start = oldText.indexOf(holder.uuid)
        val end   = start + holder.uuid.length
        (
          oldText.substring(0, start) + holder.handle + (if (end < oldText.length) oldText.substring(end) else ""),
          acc ++ Seq(holder.mention.copy(start = start + offset))
        )
      case ((oldText, acc), _) => (oldText, acc) // when Markdown deletes the mention
    }._2

  def restoreMentionHandles(text: CharSequence, mentionHolders: Seq[MentionHolder]): Spannable = {
    val ssb = SpannableStringBuilder.valueOf(text)

    mentionHolders.foldLeft(text.toString) {
      case (oldText, holder) if oldText.contains(holder.uuid) =>
        val start = oldText.indexOf(holder.uuid)
        ssb.replace(start, start + holder.uuid.length, holder.handle)
        oldText.replace(holder.uuid, holder.handle)
      case (oldText, _) => oldText // when Markdown deletes the mention
    }

    new SpannableString(ssb)
  }
}
