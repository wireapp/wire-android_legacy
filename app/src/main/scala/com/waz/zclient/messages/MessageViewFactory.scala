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

import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.zclient.{R, ViewHelper}

import scala.collection.mutable

class MessageViewFactory {

  val DefaultLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

  private val cache = new mutable.HashMap[MsgPart, mutable.Stack[MessageViewPart]]

  private val viewCache = new mutable.HashMap[Int, mutable.Stack[View]]

  def recycle(part: MessageViewPart): Unit = {
    verbose(s"recycling part: ${part.tpe}")
    cache.getOrElseUpdate(part.tpe, new mutable.Stack[MessageViewPart]()).push(part)
  }

  def get(tpe: MsgPart, parent: ViewGroup): MessageViewPart = {
    cache.get(tpe).flatMap(s => if(s.isEmpty) None else Some(s.pop())).getOrElse {
      verbose(s"there was no cached $tpe, building a new one")
      import MsgPart._
      tpe match {
        case User               => ViewHelper.inflate(R.layout.message_user, parent, false)
        case Separator          => ViewHelper.inflate(R.layout.message_separator, parent, false)
        case SeparatorLarge     => ViewHelper.inflate(R.layout.message_separator_large, parent, false)
        case Footer             => ViewHelper.inflate(R.layout.message_footer, parent, false)
        case Text               => ViewHelper.inflate(R.layout.message_text, parent, false)
        case Ping               => ViewHelper.inflate(R.layout.message_ping, parent, false)
        case Rename             => ViewHelper.inflate(R.layout.message_rename, parent, false)
        case Image              => ViewHelper.inflate(R.layout.message_image, parent, false)
        case YouTube            => ViewHelper.inflate(R.layout.message_youtube, parent, false)
        case WebLink            => ViewHelper.inflate(R.layout.message_link_preview, parent, false)
        case FileAsset          => ViewHelper.inflate(R.layout.message_file_asset, parent, false)
        case AudioAsset         => ViewHelper.inflate(R.layout.message_audio_asset, parent, false)
        case VideoAsset         => ViewHelper.inflate(R.layout.message_video_asset, parent, false)
        case Location           => ViewHelper.inflate(R.layout.message_location, parent, false)
        case MemberChange       => ViewHelper.inflate(R.layout.message_member_change, parent, false)
        case ConnectRequest     => ViewHelper.inflate(R.layout.message_connect_request, parent, false)
        case ConversationStart  => ViewHelper.inflate(R.layout.message_conversation_start, parent, false)
        case WirelessLink       => ViewHelper.inflate(R.layout.message_wireless_link, parent, false)
        case OtrMessage         => ViewHelper.inflate(R.layout.message_otr_part, parent, false)
        case SoundMedia         => ViewHelper.inflate(R.layout.message_soundmedia, parent, false)
        case MissedCall         => ViewHelper.inflate(R.layout.message_missed_call, parent, false)
        case EphemeralDots      => ViewHelper.inflate(R.layout.message_ephemeral_dots_view, parent, false)
        case WifiWarning        => ViewHelper.inflate(R.layout.message_wifi_warning, parent, false)
        case Empty              => new EmptyPartView(parent.getContext)
        case Unknown            => new EmptyPartView(parent.getContext) // TODO: display error msg, only used in internal
      }
    }
  }

  def recycle(view: View, resId: Int): Unit = {
    verbose(s"recycling view: $resId")
    viewCache.getOrElseUpdate(resId, new mutable.Stack[View]()).push(view)
  }

  def get[A <: View](resId: Int, parent: ViewGroup): A =
    viewCache.get(resId).flatMap(s => if (s.isEmpty) None else Some(s.pop().asInstanceOf[A])).getOrElse { ViewHelper.inflate[A](resId, parent, addToParent = false) }

}
