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
import android.support.v7.widget.{LinearLayoutManager, LinearSmoothScroller, RecyclerView}

class MessagesListLayoutManager(context: Context, orientation: Int, reverseLayout: Boolean) extends LinearLayoutManager(context, orientation, reverseLayout) {

  private var snapTo = LinearSmoothScroller.SNAP_TO_END

  setStackFromEnd(true)

  override def supportsPredictiveItemAnimations(): Boolean = true

  def snapToStart(): Unit = snapTo = LinearSmoothScroller.SNAP_TO_START

  def snapToEnd(): Unit = snapTo = LinearSmoothScroller.SNAP_TO_END

  override def smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int): Unit = {
    val linearSmoothScroller = new MessagesListSmoothScroller(context, snapTo)
    linearSmoothScroller.setTargetPosition(position)
    startSmoothScroll(linearSmoothScroller)
  }
}

class MessagesListSmoothScroller(context: Context, snapTo: Int) extends LinearSmoothScroller(context) {
  override def getVerticalSnapPreference: Int = snapTo
  override def getHorizontalSnapPreference: Int = snapTo
}
