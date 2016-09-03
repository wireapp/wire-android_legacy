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
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData, MessageId}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.returning
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class MessageView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val factory = inject[MessageViewFactory]
  private val selection = inject[SelectionController].messages
  private var msgId: MessageId = _

  var parent = Option.empty[ViewGroup]
  private def widthHint = parent.fold(0)(_.getWidth)

  this.onClick {
    selection.toggleFocused(msgId)
  }

  def set(pos: Int, m: MessageAndLikes, prev: Option[MessageData], focused: Boolean): Unit = {
    val msg = m.message
    msgId = msg.id
    val parts = Seq.newBuilder[(MsgPart, Option[MessageContent])]

    verbose(s"set $pos, $msg")

    if (shouldShowSeparator(msg, prev))
      parts += MsgPart.Separator -> None

    if (shouldShowChathead(msg, prev))
      parts += MsgPart.User -> None

    if (msg.msgType == Message.Type.RICH_MEDIA) {
      // add rich media parts
      msg.content foreach { content =>
        parts += MsgPart(content.tpe) -> Some(content)
      }
    } else {
      parts += MsgPart(msg.msgType) -> None
    }

    if (focused)
      parts += MsgPart.Timestamp -> None

    setParts(pos, msg, parts.result())
  }

  // TODO: system messages don't always need a divider
  private def shouldShowSeparator(msg: MessageData, prev: Option[MessageData]) = msg.msgType match {
    case Message.Type.CONNECT_REQUEST => false
    case _ =>
      prev.forall(_.time.isBefore(msg.time.minusSeconds(3600)))
  }

  private def shouldShowChathead(msg: MessageData, prev: Option[MessageData]) =
    !msg.isSystemMessage && prev.forall(m => m.userId != msg.userId || m.isSystemMessage)

  private def setParts(position: Int, msg: MessageData, parts: Seq[(MsgPart, Option[MessageContent])]) = {
    verbose(s"setParts: position: $position, parts: ${parts.map(_._1)}")

    // recycle views in reverse order, recycled views are stored in a Stack, this way we will get the same views back if parts are the same
    // XXX: one views get bigger, we may need to optimise this, we don't need to remove views that will get reused, currently this seems to be fast enough
    (0 until getChildCount).reverseIterator.map(getChildAt) foreach {
      case pv: MessageViewPart => factory.recycle(pv)
      case _ =>
    }
    removeAllViewsInLayout()

    parts.zipWithIndex foreach { case ((tpe, content), index) =>
      val view = factory.get(tpe, this)
      view.set(position, msg, content, widthHint)
      addViewInLayout(view, index, Option(view.getLayoutParams) getOrElse factory.DefaultLayoutParams)
    }
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
    case _ => returning(ViewHelper.inflate[MessageView](R.layout.message_view, parent, addToParent = false)) {
      _.parent = Some(parent)
    }
  }
}

sealed trait MsgPart

object MsgPart {
  case object Separator extends MsgPart
  case object SeparatorLarge extends MsgPart
  case object User extends MsgPart
  case object Text extends MsgPart
  case object FileAsset extends MsgPart
  case object AudioAsset extends MsgPart
  case object VideoAsset extends MsgPart
  case object Image extends MsgPart
  case object WebLink extends MsgPart
  case object YouTube extends MsgPart
  case object Location extends MsgPart
  case object SoundCloud extends MsgPart
  case object MemberChange extends MsgPart
  case object ConnectRequest extends MsgPart
  case object Timestamp extends MsgPart
  case object InviteBanner extends MsgPart

  def apply(msgType: Message.Type): MsgPart = {
    import Message.Type._
    msgType match {
      case TEXT | TEXT_EMOJI_ONLY => Text
      case ASSET => Image
      case ANY_ASSET => FileAsset
      case VIDEO_ASSET => VideoAsset
      case AUDIO_ASSET => AudioAsset
      case LOCATION => Location
      case MEMBER_JOIN | MEMBER_LEAVE => MemberChange
      case CONNECT_REQUEST => ConnectRequest
      case _ => Text // TODO
    }
  }

  def apply(msgType: Message.Part.Type): MsgPart = {
    import Message.Part.Type._

    msgType match {
      case TEXT | TEXT_EMOJI_ONLY => Text
      case ASSET => Image
      case WEB_LINK => WebLink
      case ANY_ASSET => FileAsset
      case SOUNDCLOUD => SoundCloud
      case YOUTUBE => YouTube
      case GOOGLE_MAPS | SPOTIFY => Text
      case _ => Text // TODO
    }
  }
}

trait MessageViewPart extends View {
  val tpe: MsgPart

  def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit
}

