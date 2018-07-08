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
package com.waz.zclient.cursor

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.Signal
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.ui.utils.CursorUtils

class CursorToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  setOrientation(LinearLayout.HORIZONTAL)

  val buttonWidth = getResources.getDimensionPixelSize(R.dimen.new_cursor_menu_button_width)
  val cursorItems = Signal[Seq[CursorMenuItem]]

  private var customColor = Option.empty[ColorStateList]

  cursorItems.onUi { items =>
    removeAllViews()

    val rightMargin = CursorUtils.getMarginBetweenCursorButtons(getContext)

    items.zipWithIndex foreach { case (item, i) =>
      val button: CursorIconButton = ViewHelper.inflate(R.layout.cursor_toolbar_item, this, addToParent = false)
      button.setId(item.resId)
      button.menuItem ! Some(item)
      val params = new LinearLayout.LayoutParams(buttonWidth, ViewGroup.LayoutParams.MATCH_PARENT)
      if (i < items.size - 1) params.rightMargin = rightMargin
      customColor.foreach(button.setTextColor)
      addView(button, params)
    }
  }

  def setButtonsColor(colorStateList: ColorStateList): Unit = {
    customColor = Some(colorStateList)
    (0 until getChildCount).map(getChildAt).collect { case c: CursorIconButton => c }.foreach {
      _.setTextColor(colorStateList)
    }
  }

  // TODO: implement layout, buttons should be evenly spaced (with max margin dimen R.dimen.cursor_toolbar_padding_item)
}
