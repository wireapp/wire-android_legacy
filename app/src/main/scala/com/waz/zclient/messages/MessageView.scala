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
import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData}
import com.waz.zclient.{ViewHelper, R}

class MessageView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val factory = inject[MessageViewFactory]

  // TODO: handle selection - show timestamp and backgroud/frame

  def set(pos: Int, m: MessageData, prev: Option[MessageData]): Unit = {

    // TODO: it could be faster to only recycle views that can not be reused
    // current implementation always recycles all views and fetches new parts from recycler, this is slower than just updating already added views

    for (i <- 0 until getChildCount) {
      getChildAt(i) match {
        case pv: MessageViewPart => factory.recycle(pv)
        case _ =>
      }
    }

    removeAllViewsInLayout()

    // TODO: system messages don't always need a divider

    if (prev.forall(_.time.isBefore(m.time.minusSeconds(3600))))
      addPart(pos, MsgPart.Separator, m, None)

    if (prev.forall(_.userId != m.userId)) // TODO: show also if prev is a system message
      addPart(pos, MsgPart.User, m, None)

    if (m.content.isEmpty) {
      addPart(pos, MsgPart(m.msgType), m, None)
    } else {
      // add rich media parts
      m.content foreach { content =>
        addPart(pos, MsgPart(content.tpe), m, Some(content))
      }
    }

    // TODO: add timestamp if selected
    //    requestLayout()
    //    invalidate()
  }

  private def addPart(pos: Int, tpe: MsgPart, msg: MessageData, part: Option[MessageContent]) = {
    val view = factory.get(tpe, this)
    view.set(pos, msg, part)
    addViewInLayout(view, getChildCount, Option(view.getLayoutParams) getOrElse factory.DefaultLayoutParams)
    view
  }
}

object MessageView {

  val GenericMessage = 0

  def viewType(tpe: Message.Type): Int = tpe match {
    case _ => GenericMessage
  }

  def apply(parent: ViewGroup, tpe: Int): MessageView = tpe match {
    case _ => ViewHelper.inflate(R.layout.message_view, parent, false)
  }
}

sealed trait MsgPart
object MsgPart {
  case object User extends MsgPart
  case object Separator extends MsgPart
  case object SeparatorLarge extends MsgPart
  case object Timestamp extends MsgPart
  case object Text extends MsgPart
  case object Image extends MsgPart
  case object YouTube extends MsgPart
  case object SoundCloud extends MsgPart

  def apply(msgType: Message.Type): MsgPart = msgType match {
    case Message.Type.TEXT => Text
    case Message.Type.ASSET => Image
    case _ => Text // TODO
  }

  def apply(msgType: Message.Part.Type): MsgPart = msgType match {
    case Message.Part.Type.TEXT => Text
    case Message.Part.Type.ASSET => Image
    case _ => Text // TODO
  }
}

trait MessageViewPart extends View {
  val tpe: MsgPart

  def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit
}
