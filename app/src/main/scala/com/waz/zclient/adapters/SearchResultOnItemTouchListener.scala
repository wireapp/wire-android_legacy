/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.adapters

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.{GestureDetector, MotionEvent, View}
import com.waz.model.{ConversationData, UserId}
import com.waz.zclient.views.pickuser.{ConversationRowView, UserRowView}

object SearchResultOnItemTouchListener {

  trait Callback {
    def onUserClicked(userId: UserId, position: Int, anchorView: View): Unit

    def onConversationClicked(conversation: ConversationData, position: Int): Unit

    def onUserDoubleClicked(userId: UserId, position: Int, anchorView: View): Unit
  }

}

class SearchResultOnItemTouchListener(val context: Context, var callback: SearchResultOnItemTouchListener.Callback) extends RecyclerView.OnItemTouchListener {

  private var gestureDetector: GestureDetector = null
  private var position: Int = -1
  private var rowView: View = null

  gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
    override def onDoubleTap(e: MotionEvent): Boolean = {
      rowView match {
        case view: UserRowView =>
          view.getUser.foreach(uid => callback.onUserDoubleClicked(uid, position, rowView))
        case _ =>
      }
      true
    }

    override def onSingleTapConfirmed(e: MotionEvent): Boolean = {
      rowView match {
        case view: UserRowView =>
          view.onClicked()
          view.getUser.foreach(uid => callback.onUserClicked(uid, position, rowView))
        case view: ConversationRowView =>
          callback.onConversationClicked(view.getConversation, position)
        case _ =>
      }
      true
    }
  })

  def onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean = {
    rowView = rv.findChildViewUnder(e.getX, e.getY)
    position = rv.getChildAdapterPosition(rowView)
    if (rowView.isInstanceOf[RecyclerView]) {
      return false
    }
    position = rv.getChildAdapterPosition(rowView)
    if (rowView != null && callback != null) {
      gestureDetector.onTouchEvent(e)
    }
    false
  }

  def onTouchEvent(rv: RecyclerView, e: MotionEvent): Unit = {
  }

  def onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean): Unit = {
  }
}
