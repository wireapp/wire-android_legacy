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
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData, MessageId, UserId}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.{RichOption, returning}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DateConvertUtils.asZonedDateTime
import com.waz.zclient.utils.ZTimeFormatter._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{Instant, LocalDateTime, ZoneId}

class MessageView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {

  import MessageView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val factory = inject[MessageViewFactory]
  private val selection = inject[SelectionController].messages
  private var msgId: MessageId = _

  private var separator = Option.empty[TimeSeparator]
  private var footer = Option.empty[Footer]

  var parent = Option.empty[ViewGroup]
  private def widthHint = parent.fold(0)(_.getWidth)

  this.onClick {
    selection.toggleFocused(msgId)
  }

  def set(pos: Int, mAndL: MessageAndLikes, prev: Option[MessageData], isFirstUnread: Boolean): Unit = {
    val msg = mAndL.message
    msgId = msg.id
    verbose(s"set $pos, $mAndL")

    val contentParts =
      if (msg.msgType != Message.Type.RICH_MEDIA) Seq(MsgPart(msg.msgType) -> None)
      else msg.content map { content => MsgPart(content.tpe) -> Some(content) }

    val parts =
      if (contentParts.forall(_._1 == MsgPart.Empty)) Nil // don't display anything for unknown message
      else {
        val builder = Seq.newBuilder[(MsgPart, Option[MessageContent])]

        getSeparatorType(msg, prev, isFirstUnread).foreach(sep => builder += sep -> None)

        if (shouldShowChathead(msg, prev))
          builder += MsgPart.User -> None

        // TODO: add invite banner part for first member create message
        builder ++= contentParts

        if (focusableTypes.contains(msg.msgType))//|| mAndL.likes.nonEmpty) TODO need to trigger open animation if liked
          builder += MsgPart.Footer -> None

        builder.result()
      }

    if (parts.nonEmpty) this.setMarginTop(getTopMargin(prev.map(_.msgType), parts.head._1))
    setParts(pos, mAndL, parts, isFirstUnread)
  }

  def getSeparator = separator

  def getFooter = footer

  private def getSeparatorType(msg: MessageData, prev: Option[MessageData], isFirstUnread: Boolean): Option[MsgPart] = msg.msgType match {
    case Message.Type.CONNECT_REQUEST => None
    case _ =>
      prev.fold2(None, { p =>
        val prevDay = asZonedDateTime(p.time).toLocalDate.atStartOfDay()
        val curDay = asZonedDateTime(msg.time).toLocalDate.atStartOfDay()

        if (prevDay.isBefore(curDay)) Some(SeparatorLarge)
        else if (p.time.isBefore(msg.time.minusSeconds(1800)) || isFirstUnread) Some(Separator)
        else None
      })
  }

  private def shouldShowChathead(msg: MessageData, prev: Option[MessageData]) =
    !msg.isSystemMessage && msg.msgType != Message.Type.KNOCK && prev.forall(m => m.userId != msg.userId || m.isSystemMessage)

  private def setParts(position: Int, msg: MessageAndLikes, parts: Seq[(MsgPart, Option[MessageContent])], isFirstUnread: Boolean) = {
    verbose(s"setParts: position: $position, parts: ${parts.map(_._1)}")

    // recycle views in reverse order, recycled views are stored in a Stack, this way we will get the same views back if parts are the same
    // XXX: one views get bigger, we may need to optimise this, we don't need to remove views that will get reused, currently this seems to be fast enough
    (0 until getChildCount).reverseIterator.map(getChildAt) foreach {
      case pv: ViewPart => factory.recycle(pv)
      case _ =>
    }
    removeAllViewsInLayout()

    parts.zipWithIndex foreach { case ((tpe, content), index) =>
      val view = factory.get(tpe, this)
      view match {
        case v: TimeSeparator =>
          v.set(position, msg.message.time, isFirstUnread)
          separator = Some(v)
        case v: MessageViewPart =>
          v.set(position, msg.message, content, widthHint)
        case v: Footer =>
          v.set(position, msg)
          footer = Some(v)
      }
      addViewInLayout(view, index, Option(view.getLayoutParams) getOrElse factory.DefaultLayoutParams)
    }
  }

}

object MessageView {

  val focusableTypes = Set(
    Message.Type.TEXT,
    Message.Type.TEXT_EMOJI_ONLY,
    Message.Type.ANY_ASSET,
    Message.Type.ASSET,
    Message.Type.AUDIO_ASSET,
    Message.Type.VIDEO_ASSET,
    Message.Type.LOCATION,
    Message.Type.RICH_MEDIA
  )

  val GenericMessage = 0

  def viewType(tpe: Message.Type): Int = tpe match {
    case _ => GenericMessage
  }

  def apply(parent: ViewGroup, tpe: Int): MessageView = tpe match {
    case _ => returning(ViewHelper.inflate[MessageView](R.layout.message_view, parent, addToParent = false)) {
      _.parent = Some(parent)
    }
  }

  trait MarginRule

  case object TextLike extends MarginRule
  case object ImageLike extends MarginRule
  case object FileLike extends MarginRule
  case object MemberChange extends MarginRule
  case object Rename extends MarginRule
  case object Ping extends MarginRule
  case object MissedCall extends MarginRule
  case object Other extends MarginRule

  object MarginRule {
    def apply(tpe: Message.Type): MarginRule = apply(MsgPart(tpe))

    def apply(tpe: MsgPart): MarginRule = {
      tpe match {
        case Separator |
             SeparatorLarge |
             User |
             Text => TextLike
        case MsgPart.Ping => Ping
        case MsgPart.Rename => Rename
        case FileAsset |
             AudioAsset |
             WebLink |
             YouTube |
             Location |
             SoundCloud => FileLike
        case Image | VideoAsset => ImageLike
        case MsgPart.MemberChange => MemberChange
        case _ => Other
      }
    }
  }

  def getTopMargin(prevTpe: Option[Message.Type], topPart: MsgPart)(implicit context: Context) = {
    if (prevTpe.isEmpty)
      if (MarginRule(topPart) == MemberChange) toPx(24) else 0
    else {
      val p = (MarginRule(prevTpe.get), MarginRule(topPart)) match {
        case (TextLike, TextLike)         => 8
        case (TextLike, FileLike)         => 16
        case (FileLike, FileLike)         => 10
        case (ImageLike, ImageLike)       => 0
        case (FileLike | ImageLike, _) |
             (_, FileLike | ImageLike)    => 10
        case (Rename, _)                  => 24
        case (MissedCall, _)              => 24
        case (MemberChange, _) |
             (_, MemberChange)            => 24
        case (_, Ping) | (Ping, _)        => 14
        case (_, MissedCall)              => 24
        case _                            => 0
      }
      toPx(p)
    }
  }
}

sealed trait MsgPart

object MsgPart {
  case object Separator extends MsgPart
  case object SeparatorLarge extends MsgPart
  case object User extends MsgPart
  case object Text extends MsgPart
  case object Ping extends MsgPart
  case object Rename extends MsgPart
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
  case object Footer extends MsgPart
  case object InviteBanner extends MsgPart
  case object OtrMessage extends MsgPart
  case object Empty extends MsgPart

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
      case OTR_ERROR | OTR_DEVICE_ADDED | OTR_IDENTITY_CHANGED | OTR_UNVERIFIED | OTR_VERIFIED | HISTORY_LOST | STARTED_USING_DEVICE => OtrMessage
      case KNOCK => Ping
      case RENAME => Rename
      case UNKNOWN | RICH_MEDIA => Empty
      case CONNECT_ACCEPTED | INCOMING_CALL | MISSED_CALL | RECALLED => Empty // TODO: implement view parts
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
      case GOOGLE_MAPS | SPOTIFY | TWITTER => Text
    }
  }
}

trait ViewPart extends View {
  val tpe: MsgPart
}

trait TimeSeparator extends ViewPart with ViewHelper {

  val is24HourFormat = DateFormat.is24HourFormat(getContext)

  lazy val timeText: TypefaceTextView = findById(R.id.separator__time)
  lazy val unreadDot: UnreadDot = findById(R.id.unread_dot)

  val time = Signal[Instant]()
  val text = time map { t =>
    getSeparatorTime(getContext.getResources, LocalDateTime.now, DateConvertUtils.asLocalDateTime(t), is24HourFormat, ZoneId.systemDefault, true)
  }

  text.on(Threading.Ui)(timeText.setTransformedText)

  def set(pos: Int, time: Instant, isFirstUnread: Boolean): Unit = {
    verbose(s"Setting isFirstUnread?: $isFirstUnread at pos $pos")
    this.time ! time
    unreadDot.show ! isFirstUnread
  }

  this.onClick {} //confusing if message opens when timestamp clicked

}

trait MessageViewPart extends ViewPart {
  def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit
}

trait Footer extends ViewPart {
  override val tpe = Footer

  def set(pos: Int, msg: MessageAndLikes): Unit

  def updateLikes(likedBySelf: Boolean, likes: IndexedSeq[UserId]): Unit
}


