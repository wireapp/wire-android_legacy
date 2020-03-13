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

import com.waz.api.Message
import com.waz.log.LogShow.SafeToLog

sealed trait MsgPart extends SafeToLog
sealed trait SeparatorPart extends MsgPart

object MsgPart {
  case object Separator extends SeparatorPart
  case object SeparatorLarge extends SeparatorPart
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
  case object MemberChange extends MsgPart
  case object ConnectRequest extends MsgPart
  case object Footer extends MsgPart
  case object ConversationStart extends MsgPart
  case object WirelessLink extends MsgPart
  case object OtrMessage extends MsgPart
  case object MissedCall extends MsgPart
  case object EphemeralDots extends MsgPart
  case object WifiWarning extends MsgPart
  case object MessageTimer extends MsgPart
  case object ReadReceipts extends MsgPart
  case object Composite extends MsgPart
  case object Empty extends MsgPart
  case object Unknown extends MsgPart

  case class Reply(replyType: MsgPart) extends MsgPart

  object Reply {
    def apply(msgType: Message.Type): Reply = {
      import Message.Type._
      Reply(msgType match {
        case TEXT
             | TEXT_EMOJI_ONLY
             | RICH_MEDIA => Text
        case IMAGE_ASSET        => Image
        case ANY_ASSET    => FileAsset
        case VIDEO_ASSET  => VideoAsset
        case AUDIO_ASSET  => AudioAsset
        case LOCATION     => Location
        case _            => Unknown
      })
    }
  }

  def apply(msgType: Message.Type, isOneToOne: Boolean): MsgPart = {
    import Message.Type._
    msgType match {
      case TEXT | TEXT_EMOJI_ONLY => Text
      case IMAGE_ASSET => Image
      case ANY_ASSET => FileAsset
      case VIDEO_ASSET => VideoAsset
      case AUDIO_ASSET => AudioAsset
      case LOCATION => Location
      case MEMBER_JOIN | MEMBER_LEAVE => if (isOneToOne) Empty else MemberChange //Member change information is not very interesting in One-To-One conversations
      case CONNECT_REQUEST => ConnectRequest
      case OTR_ERROR | OTR_DEVICE_ADDED | OTR_IDENTITY_CHANGED | OTR_UNVERIFIED | OTR_VERIFIED | HISTORY_LOST | STARTED_USING_DEVICE | OTR_MEMBER_ADDED => OtrMessage
      case KNOCK => Ping
      case RENAME => Rename
      case MISSED_CALL => MissedCall
      case SUCCESSFUL_CALL => Empty //TODO
      case RECALLED => Empty // recalled messages only have an icon in header
      case CONNECT_ACCEPTED => Empty // those are never used in messages (only in notifications)
      case RICH_MEDIA => Empty // RICH_MEDIA will be handled separately
      case MESSAGE_TIMER => MessageTimer
      case READ_RECEIPTS_ON | READ_RECEIPTS_OFF => if (isOneToOne) Empty else ReadReceipts
      case COMPOSITE => Composite
      case _ => Unknown
    }
  }

  def apply(msgType: Message.Part.Type): MsgPart = {
    import Message.Part.Type._
    msgType match {
      case TEXT | TEXT_EMOJI_ONLY => Text
      case ASSET => Image
      case WEB_LINK => WebLink
      case ANY_ASSET => FileAsset
      case SOUNDCLOUD => WebLink
      case SPOTIFY => WebLink
      case YOUTUBE => YouTube
      case GOOGLE_MAPS | TWITTER => WebLink
    }
  }


}
