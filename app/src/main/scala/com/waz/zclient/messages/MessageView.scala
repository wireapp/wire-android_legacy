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
package com.waz.zclient.messages

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData, MessageId}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.returning
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.{R, ViewHelper}

class MessageView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val factory = inject[MessageViewFactory]
  private val selection = inject[SelectionController].messages
  private var msgId: MessageId = _

  var parent = Option.empty[ViewGroup]
  private def widthHint = parent.fold(0)(_.getWidth)

  setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = selection.toggleFocused(msgId)
  })

  def set(pos: Int, m: MessageAndLikes, prev: Option[MessageData], focused: Boolean): Unit = {
    val msg = m.message
    msgId = msg.id
    val parts = Seq.newBuilder[(MsgPart, Option[MessageContent])]

    if (shouldShowSeparator(msg, prev))
      parts += MsgPart.Separator -> None

    if (shouldShowChathead(msg, prev))
      parts += MsgPart.User -> None

    if (msg.content.isEmpty) {
      parts += MsgPart(msg.msgType) -> None
    } else {
      // add rich media parts
      msg.content foreach { content =>
        parts += MsgPart(content.tpe) -> Some(content)
      }
    }

    if (focused)
      parts += MsgPart.Timestamp -> None

    setParts(pos, msg, parts.result())
  }

  // TODO: system messages don't always need a divider
  private def shouldShowSeparator(msg: MessageData, prev: Option[MessageData]) =
    prev.forall(_.time.isBefore(msg.time.minusSeconds(3600)))

  private def shouldShowChathead(msg: MessageData, prev: Option[MessageData]) =
    prev.forall(m => m.userId != msg.userId || m.isSystemMessage)

  private def setParts(position: Int, msg: MessageData, parts: Seq[(MsgPart, Option[MessageContent])]) = {

    val partViews = Seq.tabulate(getChildCount)(getChildAt).collect { case pv: MessageViewPart => pv }.iterator.buffered

    parts.zipWithIndex foreach { case ((tpe, content), index) =>
      while (partViews.hasNext && partViews.head.tpe != tpe && partViews.head.tpe.order <= tpe.order) {
        factory.recycle(returning(partViews.next()) { removeViewInLayout })
      }
      if (partViews.hasNext && partViews.head.tpe == tpe) {
        partViews.next().set(position, msg, content, widthHint)
      } else {
        val view = factory.get(tpe, this)
        view.set(position, msg, content, widthHint)
        addViewInLayout(view, index, Option(view.getLayoutParams) getOrElse factory.DefaultLayoutParams)
      }
    }

    partViews foreach { pv => factory.recycle(pv) }
    removeViewsInLayout(parts.length, getChildCount - parts.length)
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    super.onLayout(changed, l, t, r, b)
    verbose(s"onLayout, height: ${b - t}")
  }
}

object MessageView {

  val GenericMessage = 0

  def viewType(tpe: Message.Type): Int = tpe match {
    case _ => GenericMessage
  }

  def apply(parent: ViewGroup, tpe: Int): MessageView = tpe match {
    case _ => returning(ViewHelper.inflate[MessageView](R.layout.message_view, parent, addToParent = false)) { _.parent = Some(parent) }
  }
}

/**
  * @param order - describes typical ordering of parts in a message, used to optimize view recycling
  */
sealed abstract class MsgPart(val order: Int)

object MsgPart {
  case object Separator extends MsgPart(0)
  case object SeparatorLarge extends MsgPart(0)
  case object User extends MsgPart(10)
  case object Text extends MsgPart(20)
  case object Image extends MsgPart(30)
  case object WebLink extends MsgPart(40)
  case object YouTube extends MsgPart(40)
  case object SoundCloud extends MsgPart(40)
  case object Timestamp extends MsgPart(100)

  def apply(msgType: Message.Type): MsgPart = msgType match {
    case Message.Type.TEXT => Text
    case Message.Type.ASSET => Image
    case _ => Text // TODO
  }

  def apply(msgType: Message.Part.Type): MsgPart = msgType match {
    case Message.Part.Type.TEXT => Text
    case Message.Part.Type.ASSET => Image
    case Message.Part.Type.WEB_LINK => WebLink
    case _ => Text // TODO
  }
}

trait MessageViewPart extends View {
  val tpe: MsgPart

  def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit
}

