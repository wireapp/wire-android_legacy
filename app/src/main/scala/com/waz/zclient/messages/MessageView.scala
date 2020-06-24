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
package com.waz.zclient.messages

import android.content.Context
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, ViewGroup}
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.{RichOption, RichWireInstant}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageViewLayout.PartDesc
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.footer.FooterPartView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DateConvertUtils.asZonedDateTime
import com.waz.zclient.utils._
import com.waz.zclient.{BuildConfig, R, ViewHelper}

import scala.concurrent.duration._

class MessageView(context: Context, attrs: AttributeSet, style: Int)
    extends MessageViewLayout(context, attrs, style) with ViewHelper {

  import MessageView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  protected val factory = inject[MessageViewFactory]
  private val selection = inject[ConversationController].messages
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

  def set(mAndL: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions, adapter: MessagesPagedListAdapter): Unit = {
    val animateFooter = msgId == mAndL.message.id && hasFooter != shouldShowFooter(mAndL, opts)
    hasFooter = shouldShowFooter(mAndL, opts)
    data = mAndL
    msg = mAndL.message
    msgId = msg.id

    import opts._
    //if we have just added a user to a conversation with a bot, we shouldn't count this as oneToOne,
    //so we check the members size also
    val isOneToOne = !isGroup && mAndL.message.members.size <= 2

    val contentParts = {
      if (msg.msgType == Message.Type.MEMBER_JOIN && msg.firstMessage) {
        (if (msg.name.nonEmpty) Seq(PartDesc(ConversationStart)) else Seq.empty) ++
          (if (msg.members.nonEmpty) Seq(PartDesc(MemberChange)) else Seq.empty) ++
          (if (canHaveLink) Seq(PartDesc(WirelessLink)) else Seq.empty)
      }
      else {
        val quotePart = (mAndL.quote, mAndL.message.quote) match {
          case (Some(quote), Some(qInfo)) if qInfo.validity => Seq(PartDesc(Reply(quote.msgType)))
          case (Some(_), Some(_))                           => Seq(PartDesc(Reply(Unknown))) // the quote is invalid
          case (None, Some(_))                              => Seq(PartDesc(Reply(Unknown))) // the quote was deleted
          case _                                            => Seq[PartDesc]()
        }

        quotePart ++
          (if (msg.msgType == Message.Type.RICH_MEDIA) {
            val contentWithOG = msg.content.filter(_.openGraph.isDefined)
            if (contentWithOG.size == 1 && msg.content.size == 1)
              msg.content.map(content => PartDesc(MsgPart(content.tpe), Some(content)))
            else if (msg.content.headOption.nonEmpty && msg.content.head.tpe.equals(Message.Part.Type.YOUTUBE) && msg.content.size == 1)
                Seq(PartDesc(MsgPart(msg.content.head.tpe), Some(msg.content.head)))
            else
              Seq(PartDesc(MsgPart(Message.Type.TEXT, isOneToOne))) ++ contentWithOG.map(content => PartDesc(MsgPart(content.tpe), Some(content))).filter(_.tpe == WebLink)
          }
          else Seq(PartDesc(MsgPart(msg.msgType, isOneToOne))))
      }
    } .filter(_.tpe != MsgPart.Empty)

    val parts =
      if (!BuildConfig.DEBUG && msg.msgType != Message.Type.RECALLED && contentParts.forall(_.tpe == MsgPart.Unknown)) Nil // don't display anything for unknown message
      else {
        val builder = Seq.newBuilder[PartDesc]

        getSeparatorType(msg, prev, isFirstUnread).foreach(sep => builder += PartDesc(sep))

        if (shouldShowChathead(msg, prev))
          builder += PartDesc(MsgPart.User)

        builder ++= contentParts

        if (msg.msgType == Message.Type.IMAGE_ASSET && !areDownloadsAlwaysEnabled)
          builder += PartDesc(MsgPart.WifiWarning)

        if (hasFooter || animateFooter)
          builder += PartDesc(MsgPart.Footer)

        builder.result()
      }

    val (top, bottom) = if (parts.isEmpty) (0, 0) else getMargins(prev.map(_.msgType), next.map(_.msgType), parts.head.tpe, parts.last.tpe, isOneToOne)
    setPadding(0, top, 0, bottom)
    setParts(mAndL, parts, opts, adapter)

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
        val prevDay = asZonedDateTime(p.time.instant).toLocalDate.atStartOfDay()
        val curDay = asZonedDateTime(msg.time.instant).toLocalDate.atStartOfDay()

        if (prevDay.isBefore(curDay)) Some(SeparatorLarge)
        else if (p.time.isBefore(msg.time - 1800.seconds) || isFirstUnread) Some(Separator)
        else None
      })
  }

  private def systemMessage(m: MessageData) = {
    import Message.Type._
    m.isSystemMessage || (m.msgType match {
      case OTR_DEVICE_ADDED | OTR_UNVERIFIED | OTR_VERIFIED | STARTED_USING_DEVICE | OTR_MEMBER_ADDED | MESSAGE_TIMER=> true
      case _ => false
    })
  }

  private def shouldShowChathead(msg: MessageData, prev: Option[MessageData]) = {
    val userChanged = prev.forall(m => m.userId != msg.userId || systemMessage(m))
    val recalled = msg.msgType == Message.Type.RECALLED
    val edited = msg.isEdited
    val knock = msg.msgType == Message.Type.KNOCK

    !knock && !systemMessage(msg) && (recalled || edited || userChanged)
  }

  private def shouldShowFooter(mAndL: MessageAndLikes, opts: MsgBindOptions): Boolean = {
    mAndL.likes.nonEmpty ||
      selection.isFocused(mAndL.message.id) ||
      opts.isLastSelf ||
      mAndL.message.isFailed
  }

  def getFooter = listParts.lastOption.collect { case footer: FooterPartView => footer }
}

object MessageView extends DerivedLogTag {

  import Message.Type._

  val clickableTypes = Set(
    TEXT,
    TEXT_EMOJI_ONLY,
    ANY_ASSET,
    IMAGE_ASSET,
    AUDIO_ASSET,
    VIDEO_ASSET,
    LOCATION,
    RICH_MEDIA
  )

  val longClickableTypes = clickableTypes ++ Set(
    KNOCK,
    COMPOSITE
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
             Location => FileLike
        case Image | VideoAsset => ImageLike
        case MemberChange |
             OtrMessage |
             Rename |
             WirelessLink |
             ConversationStart |
             MessageTimer |
             ReadReceipts => SystemLike
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
          case SystemLike => 8
          case _ => 0
        }
      else 0

    (toPx(top), toPx(bottom))
  }

  // Message properties calculated while binding, may not be directly related to message state,
  // should not be cached in message view as those can be valid only while set method is called
  case class MsgBindOptions(position: Int,
                            isSelf: Boolean,
                            isLast: Boolean,
                            isLastSelf: Boolean, // last self message in conv
                            isFirstUnread: Boolean,
                            listDimensions: Dim2,
                            isGroup: Boolean,
                            teamId: Option[TeamId],
                            canHaveLink: Boolean,
                            selfId: Option[UserId])
}



