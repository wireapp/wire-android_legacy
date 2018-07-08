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
package com.waz.zclient.conversation.toolbar

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v4.view.GestureDetectorCompat
import android.util.AttributeSet
import android.view._
import android.widget.LinearLayout
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal, SourceSignal}
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.{R, ViewHelper}

trait ToolbarItem {
  val resId: Int
  val glyphResId: Int
  val stringId: Int
  val timedGlyphResId: Int
}

case class MessageActionToolbarItem(action: MessageAction) extends ToolbarItem {
  val resId = action.resId
  val glyphResId = action.glyphResId
  val stringId = action.stringId
  val timedGlyphResId = action.glyphResId
}

case class CursorActionToolbarItem(cursorItem: CursorMenuItem) extends ToolbarItem {
  val resId = cursorItem.resId
  val glyphResId: Int = cursorItem.glyphResId
  val stringId = cursorItem.resTooltip
  val timedGlyphResId = cursorItem.timedGlyphResId
}

case object DummyToolbarItem extends ToolbarItem {
  val resId = R.id.cursor_menu_item_dummy
  val glyphResId = R.string.empty_string
  val stringId = R.string.empty_string
  val timedGlyphResId = R.string.empty_string
}

case object MoreToolbarItem extends ToolbarItem {
  val resId = R.id.cursor_menu_item_more
  val glyphResId = R.string.glyph__more
  val stringId = R.string.tooltip_more
  val timedGlyphResId = R.string.glyph__more
}

class CustomToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private lazy val detector = new GestureDetectorCompat(getContext, gestureListener)

  private var touchedButtonContainer: View = null
  private var isEphemeralMode: Boolean = false

  val cursorItems: SourceSignal[Seq[ToolbarItem]] = Signal(Seq())
  val onCursorButtonClicked = EventStream[ToolbarItem]()
  val onCursorButtonLongPressed = EventStream[ToolbarItem]()
  val onMotionEvent = EventStream[(ToolbarItem, MotionEvent)]()
  val onShowTooltip = EventStream[(ToolbarItem, String, View)]()

  setOrientation(LinearLayout.HORIZONTAL)

  private val gestureListener: GestureDetector.OnGestureListener = new GestureDetector.SimpleOnGestureListener() {
    override def onDown(e: MotionEvent): Boolean = true

    override def onLongPress(e: MotionEvent): Unit = {
      val item: ToolbarItem = touchedButtonContainer.getTag.asInstanceOf[ToolbarItem]
      if (item ne DummyToolbarItem) {
        onShowTooltip ! (item, getResources.getString(item.stringId), touchedButtonContainer)
      }
      onCursorButtonLongPressed ! item
    }

    override def onSingleTapConfirmed(e: MotionEvent): Boolean = {
      val item: ToolbarItem = touchedButtonContainer.getTag.asInstanceOf[ToolbarItem]
      onCursorButtonClicked ! item
      true
    }
  }

  def showEphemeralMode(color: Int): Unit = {
    isEphemeralMode = true
    getButtons.foreach { button => button.showEphemeralMode(color) }
  }

  def hideEphemeraMode(color: Int): Unit = {
    isEphemeralMode = false
    getButtons.foreach { button => button.hideEphemeralMode(color) }
  }

  cursorItems.on(Threading.Ui){ items =>
    createItems(items)
  }

  def createItems(items: Seq[ToolbarItem]): Unit = {
    removeAllViews()
    items.foreach{ item =>
      val button = new ToolbarNormalButton(context)
      button.setToolbarItem(item)
      if (item != DummyToolbarItem) {
        button.setPressedBackgroundColor(ContextCompat.getColor(getContext, R.color.light_graphite))
        button.setOnTouchListener(new View.OnTouchListener() {
          def onTouch(view: View, motionEvent: MotionEvent): Boolean = {
            val item: ToolbarItem = view.getTag.asInstanceOf[ToolbarItem]
            touchedButtonContainer = button
            onMotionEvent ! (item, motionEvent)
            detector.onTouchEvent(motionEvent)
            false
          }
        })
      }
      addView(button)
    }
  }

  def getButtons: Seq[ToolbarButton] = {
    (0 until getChildCount).flatMap(i => Option(getChildAt(i).asInstanceOf[ToolbarButton]))
  }

  def getButtonForItem(item: ToolbarItem): Option[ToolbarButton] = {
    getButtons.find(_.getToolbarItem == item)
  }
}
