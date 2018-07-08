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
import android.widget.{FrameLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.threading.Threading
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

//TODO button animation
class LikeButton(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_button)

  private val likeButtonConstant: TextView = findById(R.id.like__button_constant)
  private val likeButtonAnimation: TextView = findById(R.id.like__button_animation)

  def init(controller: FooterViewController): Unit = {

    val likeBtnState = controller.likedBySelf map { liked =>
      (getColor(if (liked) R.color.accent_red else R.color.text__secondary_light),
        if (liked) R.string.glyph__liked else R.string.glyph__like)
    }

    likeBtnState.on(Threading.Ui) { case (color, glyphResId) =>
      likeButtonConstant.setTextColor(color)
      likeButtonConstant.setText(glyphResId)
    }

    likeButtonConstant onClick {
      verbose("Like button clicked")
      controller.onLikeClicked()
    }
  }
}
