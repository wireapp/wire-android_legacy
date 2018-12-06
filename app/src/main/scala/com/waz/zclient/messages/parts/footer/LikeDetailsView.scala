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
package com.waz.zclient.messages.parts.footer

import android.content.Context
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.UserId
import com.waz.utils.events.Signal
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class LikeDetailsView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_details)
  setOrientation(LinearLayout.HORIZONTAL)

  private val description: TextView = findById(R.id.like__description)

  def init(controller: FooterViewController): Unit = {
    val likedBy = controller.messageAndLikes.map(_.likes.sortBy(_.str))

    def getDisplayNameString(ids: Seq[UserId]): Signal[String] = {
      if (ids.size > 3)
        Signal.const(getQuantityString(R.plurals.message_footer__number_of_likes, ids.size, Integer.valueOf(ids.size)))
      else
        Signal.sequence(ids map { controller.signals.displayNameStringIncludingSelf } :_*).map { names =>
          if (names.isEmpty) getString(R.string.message_footer__tap_to_like)
          else names.mkString(", ")
        }
    }

    val displayText = likedBy.flatMap(getDisplayNameString)

    displayText.onUi(description.setText)
  }
}

