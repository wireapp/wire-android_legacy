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
package com.waz.zclient.collection.views

import android.content.Context
import android.support.v7.widget.RecyclerView.OnScrollListener
import android.support.v7.widget.{GridLayoutManager, LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.View.OnTouchListener
import android.view.{MotionEvent, View}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.Dim2
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.collection.CollectionSpanSizeLookup
import com.waz.zclient.collection.adapters.CollectionAdapter
import com.waz.zclient.collection.controllers.CollectionScrollController.Scroll
import com.waz.zclient.collection.controllers.{CollectionController, CollectionScrollController}
import com.waz.zclient.collection.fragments.CollectionFragment
import com.waz.zclient.pages.main.conversation.collections.CollectionItemDecorator

class CollectionRecyclerView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import CollectionRecyclerView._

  val viewDim = Signal[Dim2]()
  val layoutManager = new GridLayoutManager(context, CollectionController.GridColumns, LinearLayoutManager.VERTICAL, false) {
    override def supportsPredictiveItemAnimations(): Boolean = true
  }
  var collectionItemDecorator: CollectionItemDecorator = null

  def init(adapter: CollectionAdapter): Unit = {
    val scrollController = new CollectionScrollController(adapter, viewDim.map(_.height))
    collectionItemDecorator = new CollectionItemDecorator(adapter, CollectionController.GridColumns)

    setAdapter(adapter)

    layoutManager.setSpanSizeLookup(new CollectionSpanSizeLookup(CollectionController.GridColumns, adapter))
    setLayoutManager(layoutManager)

    addItemDecoration(collectionItemDecorator)

    scrollController.onScroll { case Scroll(pos, smooth) =>
      verbose(s"Scrolling to pos: $pos, smooth: $smooth")
      val scrollTo = math.min(adapter.getItemCount - 1, pos)
      if (smooth) {
        val current = layoutManager.findFirstVisibleItemPosition()
        // jump closer to target position before scrolling, don't want to smooth scroll through many messages
        if (math.abs(current - pos) > MaxSmoothScroll)
          layoutManager.scrollToPosition(if (pos > current) pos - MaxSmoothScroll else pos + MaxSmoothScroll)

        smoothScrollToPosition(pos) //TODO figure out how to provide an offset, we should scroll to top of the message
      } else {
        layoutManager.scrollToPosition(scrollTo)
      }
    }

    addOnScrollListener(new OnScrollListener {
      override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
        newState match {
          case RecyclerView.SCROLL_STATE_IDLE =>
            scrollController.onScrolled(layoutManager.findLastCompletelyVisibleItemPosition())
          case RecyclerView.SCROLL_STATE_DRAGGING =>
            scrollController.onDragging()
          case _ =>
        }
      }
    })

    setOnTouchListener(new OnTouchListener {
      var headerDown = false

      override def onTouch(v: View, event: MotionEvent): Boolean = {
        val x = Math.round(event.getX)
        val y = Math.round(event.getY)
        event.getAction match {
          case MotionEvent.ACTION_DOWN =>
            if (collectionItemDecorator.getHeaderClicked(x, y) < 0) {
              headerDown = false
            } else {
              headerDown = true
            }
            false
          case MotionEvent.ACTION_MOVE =>
            if (event.getHistorySize > 0) {
              val deltaX = event.getHistoricalX(0) - x
              val deltaY = event.getHistoricalY(0) - y
              if (Math.abs(deltaY) + Math.abs(deltaX) > CollectionFragment.MAX_DELTA_TOUCH) {
                headerDown = false
              }
            }
            false
          case MotionEvent.ACTION_UP if !headerDown => false
          case MotionEvent.ACTION_UP => adapter.onHeaderClicked(collectionItemDecorator.getHeaderClicked(x, y))
          case _ => false
        }
      }
    })
  }
  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    viewDim ! Dim2(r - l, b - t)
    super.onLayout(changed, l, t, r, b)
  }

  def getSpanSizeLookup(): CollectionSpanSizeLookup ={
    getLayoutManager.asInstanceOf[GridLayoutManager].getSpanSizeLookup.asInstanceOf[CollectionSpanSizeLookup]
  }

  override def onInterceptTouchEvent(event: MotionEvent): Boolean = {
    val superIntercept = super.onInterceptTouchEvent(event)
    if (collectionItemDecorator == null) {
      superIntercept
    } else {
      val x = Math.round(event.getX)
      val y = Math.round(event.getY)
      val shouldIntercept = collectionItemDecorator.getHeaderClicked(x, y) >= 0
      superIntercept || shouldIntercept
    }
  }
}

object CollectionRecyclerView {

  val MaxSmoothScroll = 50
}
