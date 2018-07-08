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
import com.waz.model.UserId
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.parts.ChatheadsRecyclerView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.common.controllers.ScreenController

class LikeDetailsView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  import LikeDetailsView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_details)
  setOrientation(LinearLayout.HORIZONTAL)

  private val description: TextView = findById(R.id.like__description)
  private val chatheadContainer: LikersListView = findById(R.id.like_chathead_container)
  private val gtvMore: TextView = findById(R.id.gtv_more)

  def init(controller: FooterViewController): Unit = {
    val likedBy = controller.messageAndLikes.map(_.likes)

    def getDisplayNameString(ids: Seq[UserId]): Signal[String] = {
      if (showAvatars(ids)) Signal const getQuantityString(R.plurals.message_footer__number_of_likes, ids.size, Integer.valueOf(ids.size))
      else for {
        zms <- controller.zms
        names <- Signal.sequence(ids map { controller.signals.displayNameStringIncludingSelf } :_*)
      } yield
        if (names.isEmpty) getString(R.string.message_footer__tap_to_like)
        else names.mkString(", ")
    }

    def showLikers() = controller.message.map(_.id).head.foreach { mId =>
      inject[ScreenController].showLikesForMessage ! Some(mId)
    }(Threading.Ui)

    likedBy.on(Threading.Ui) { ids =>
      val show = showAvatars(ids)
      chatheadContainer.setVisible(show)
      gtvMore.setVisible(show)
      chatheadContainer.users ! ids.take(if (show) 2 else 0) // setting to 0 to recycle chatheads
    }

    val displayText = likedBy flatMap getDisplayNameString

    displayText.on(Threading.Ui)(description.setText)

    chatheadContainer.onClick(showLikers())
    gtvMore.onClick(showLikers())
  }
}

object LikeDetailsView {
  def showAvatars(ids: Seq[UserId]) = ids.size > 2
}

class LikersListView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ChatheadsRecyclerView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val chatHeadResId: Int = R.layout.message_like_chathead
}
