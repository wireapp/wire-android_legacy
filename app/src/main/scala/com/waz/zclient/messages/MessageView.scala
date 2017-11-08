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
import android.view.{HapticFeedbackConstants, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{Dim2, MessageData, MessageId}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.RichOption
import com.waz.zclient.controllers.AssetsController
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MessageViewLayout.PartDesc
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.footer.FooterPartView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DateConvertUtils.asZonedDateTime
import com.waz.zclient.utils._
import com.waz.zclient.{BuildConfig, R, ViewHelper}
import org.threeten.bp.Instant

class MessageView(context: Context, attrs: AttributeSet, style: Int)
    extends MessageViewLayout(context, attrs, style) with ViewHelper {

  import MessageView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  protected val factory = inject[MessageViewFactory]
  private val selection = inject[SelectionController].messages
  private lazy val messageActions = inject[MessageActionsController]
  private lazy val assetsController = inject[AssetsController]

  private var msgId: MessageId = _
  private var msg: MessageData = MessageData.Empty
  private var data: MessageAndLikes = MessageAndLikes.Empty

  private var hasFooter = false

  setClipChildren(false)
  setClipToPadding(false)

  this.onClick {
    if (clickableTypes.contains(msg.msgType))
      selection.toggleFocused(msgId)
  }

  this.onLongClick {
    if (longClickableTypes.contains(msg.msgType)) {
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      messageActions.showDialog(data)
    } else false
  }

  def set(mAndL: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): Unit = {
    val animateFooter = msgId == mAndL.message.id && hasFooter != shouldShowFooter(mAndL, opts)
    hasFooter = shouldShowFooter(mAndL, opts)
    data = mAndL
    msg = mAndL.message
    msgId = msg.id

    val isOneToOne = ConversationType.isOneToOne(opts.convType)

    val contentParts = {
      if (msg.msgType == Message.Type.RICH_MEDIA){
        if (msg.content.size > 1){
          Seq(PartDesc(MsgPart(Message.Type.TEXT, isOneToOne))) ++ (msg.content map { content => PartDesc(MsgPart(content.tpe), Some(content)) }).filter(_.tpe == WebLink)
        } else {
          msg.content map { content => PartDesc(MsgPart(content.tpe), Some(content)) }
        }
      }
      else
        Seq(PartDesc(MsgPart(msg.msgType, isOneToOne)))
    } .filter(_.tpe != MsgPart.Empty)

    val parts =
      if (!BuildConfig.DEBUG && msg.msgType != Message.Type.RECALLED && contentParts.forall(_.tpe == MsgPart.Unknown)) Nil // don't display anything for unknown message
      else {
        val builder = Seq.newBuilder[PartDesc]

        getSeparatorType(msg, prev, opts.isFirstUnread).foreach(sep => builder += PartDesc(sep))

        if (shouldShowChathead(msg, prev))
          builder += PartDesc(MsgPart.User)

        if (shouldShowInviteBanner(msg, opts)) {
          builder += PartDesc(MsgPart.InviteBanner)
        }
        builder ++= contentParts

        if (msg.isEphemeral) {
          builder += PartDesc(MsgPart.EphemeralDots)
        }

        if (msg.msgType == Message.Type.ASSET && !areDownloadsAlwaysEnabled)
          builder += PartDesc(MsgPart.WifiWarning)

        if (hasFooter || animateFooter)
          builder += PartDesc(MsgPart.Footer)

        builder.result()
      }

    val (top, bottom) = if (parts.isEmpty) (0, 0) else getMargins(prev.map(_.msgType), next.map(_.msgType), parts.head.tpe, parts.last.tpe, isOneToOne)
    setPadding(0, top, 0, bottom)
    setParts(mAndL, parts, opts)

    if (animateFooter)
      getFooter foreach { footer =>
        if (hasFooter) footer.slideContentIn()
        else footer.slideContentOut()
      }
  }

  def areDownloadsAlwaysEnabled = assetsController.downloadsAlwaysEnabled.currentValue.contains(true)

  def isFooterHiding = !hasFooter && getFooter.isDefined

  def isEphemeral = msg.isEphemeral

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

  private def systemMessage(m: MessageData) = {
    import Message.Type._
    m.isSystemMessage || (m.msgType match {
      case OTR_DEVICE_ADDED | OTR_UNVERIFIED | OTR_VERIFIED | STARTED_USING_DEVICE | OTR_MEMBER_ADDED => true
      case _ => false
    })
  }

  private def shouldShowChathead(msg: MessageData, prev: Option[MessageData]) = {
    val userChanged = prev.forall(m => m.userId != msg.userId || systemMessage(m))
    val recalled = msg.msgType == Message.Type.RECALLED
    val edited = msg.editTime != Instant.EPOCH
    val knock = msg.msgType == Message.Type.KNOCK

    !knock && !systemMessage(msg) && (recalled || edited || userChanged)
  }

  private def shouldShowInviteBanner(msg: MessageData, opts: MsgBindOptions) =
    opts.position == 0 && msg.msgType == Message.Type.MEMBER_JOIN && opts.convType == ConversationType.Group

  private def shouldShowFooter(mAndL: MessageAndLikes, opts: MsgBindOptions): Boolean = {
    mAndL.likes.nonEmpty ||
      selection.isFocused(mAndL.message.id) ||
      (opts.isLastSelf && opts.convType != ConversationType.Group) ||
      mAndL.message.state == Message.Status.FAILED || mAndL.message.state == Message.Status.FAILED_READ
  }

  def getFooter = listParts.lastOption.collect { case footer: FooterPartView => footer }
}

object MessageView {

  import Message.Type._

  val clickableTypes = Set(
    TEXT,
    TEXT_EMOJI_ONLY,
    ANY_ASSET,
    ASSET,
    AUDIO_ASSET,
    VIDEO_ASSET,
    LOCATION,
    RICH_MEDIA
  )

  val longClickableTypes = clickableTypes ++ Set(
    KNOCK
  )

  val GenericMessage = 0

  def viewType(tpe: Message.Type): Int = tpe match {
    case _ => GenericMessage
  }

  def apply(parent: ViewGroup, tpe: Int): MessageView = tpe match {
    case _ => ViewHelper.inflate[MessageView](R.layout.message_view, parent, addToParent = false)
  }

  trait MarginRule

  case object TextLike extends MarginRule
  case object SeparatorLike extends MarginRule
  case object ImageLike extends MarginRule
  case object FileLike extends MarginRule
  case object SystemLike extends MarginRule
  case object Ping extends MarginRule
  case object MissedCall extends MarginRule
  case object Other extends MarginRule

  object MarginRule {
    def apply(tpe: Message.Type, isOneToOne: Boolean): MarginRule = apply(MsgPart(tpe, isOneToOne))

    def apply(tpe: MsgPart): MarginRule = {
      tpe match {
        case Separator |
             SeparatorLarge |
             User |
             Text => TextLike
        case MsgPart.Ping => Ping
        case FileAsset |
             AudioAsset |
             WebLink |
             YouTube |
             Location |
             SoundMedia => FileLike
        case Image | VideoAsset => ImageLike
        case MsgPart.MemberChange |
             MsgPart.OtrMessage |
             MsgPart.Rename => SystemLike
        case MsgPart.MissedCall => MissedCall
        case _ => Other
      }
    }
  }

  def getMargins(prevTpe: Option[Message.Type], nextTpe: Option[Message.Type], topPart: MsgPart, bottomPart: MsgPart, isOneToOne: Boolean)(implicit context: Context): (Int, Int) = {
    val top =
      if (prevTpe.isEmpty)
        MarginRule(topPart) match {
          case SystemLike => 24
          case _ => 0
        }
      else {
        (MarginRule(prevTpe.get, isOneToOne), MarginRule(topPart)) match {
          case (TextLike, TextLike)         => 8
          case (TextLike, FileLike)         => 16
          case (FileLike, FileLike)         => 10
          case (ImageLike, ImageLike)       => 4
          case (FileLike | ImageLike, _) |
               (_, FileLike | ImageLike)    => 10
          case (MissedCall, _)              => 24
          case (SystemLike, _) |
               (_, SystemLike)              => 24
          case (_, Ping) | (Ping, _)        => 14
          case (_, MissedCall)              => 24
          case _                            => 0
        }
      }

    val bottom =
      if (nextTpe.isEmpty)
        MarginRule(bottomPart) match {
          case SystemLike => 24
          case _ => 0
        }
      else 0

    (toPx(top), toPx(bottom))
  }

  // Message properties calculated while binding, may not be directly related to message state,
  // should not be cached in message view as those can be valid only while set method is called
  case class MsgBindOptions(
                             position: Int,
                             isSelf: Boolean,
                             isLast: Boolean,
                             isLastSelf: Boolean, // last self message in conv
                             isFirstUnread: Boolean,
                             listDimensions: Dim2,
                             convType: ConversationType
                       )
}



