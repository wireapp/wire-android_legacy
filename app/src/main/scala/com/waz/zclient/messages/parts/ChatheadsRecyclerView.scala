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
package com.waz.zclient.messages.parts

import android.view.ViewGroup
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.model.UserId
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.MessageViewFactory
import com.waz.zclient.{R, ViewHelper}

trait ChatheadsRecyclerView extends ViewGroup with ViewHelper {
  val cache = inject[MessageViewFactory]
  val chatHeadResId = R.layout.message_member_chathead

  val users = Signal[Seq[UserId]]()

  users { ids =>
    verbose(s"user id: $ids")
    if (getChildCount > ids.length) {
      for (i <- ids.length until getChildCount) cache.recycle(getChildAt(i), chatHeadResId)
      removeViewsInLayout(ids.length, getChildCount - ids.length)
    }

    ids.zipWithIndex foreach { case (id, index) =>
      val view =
        if (index < getChildCount) getChildAt(index).asInstanceOf[ChatheadView]
        else returning(cache.get[ChatheadView](chatHeadResId, this)) { addView }

      view.setUserId(id)
    }
  }
}
