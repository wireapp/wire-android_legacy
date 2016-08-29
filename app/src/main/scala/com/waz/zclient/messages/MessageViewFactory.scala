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

import android.view.ViewGroup
import android.widget.LinearLayout
import com.waz.zclient.{R, ViewHelper}

import scala.collection.mutable

class MessageViewFactory {

  val DefaultLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

  private val cache = new mutable.HashMap[MsgPart, mutable.Queue[MessageViewPart]]

  def recycle(view: MessageViewPart): Unit =
    cache.getOrElseUpdate(view.tpe, new mutable.Queue[MessageViewPart]()).enqueue(view)

  def get(tpe: MsgPart, parent: ViewGroup): MessageViewPart =
    cache.get(tpe).flatMap(q => if(q.isEmpty) None else Some(q.dequeue())).getOrElse {
      import MsgPart._
      tpe match {
        case User           => ViewHelper.inflate(R.layout.message_user, parent, false)
        case Separator      => ViewHelper.inflate(R.layout.message_separator, parent, false)
        case SeparatorLarge => ViewHelper.inflate(R.layout.message_separator_large, parent, false)
        case Timestamp      => ViewHelper.inflate(R.layout.message_timestamp, parent, false)
        case Text           => ViewHelper.inflate(R.layout.message_text, parent, false)
        case Image          => ViewHelper.inflate(R.layout.message_image, parent, false)
        case WebLink        => ViewHelper.inflate(R.layout.message_link_preview, parent, false)
        case _              => ViewHelper.inflate(R.layout.message_text, parent, false) // TODO: other types
      }
    }
}
