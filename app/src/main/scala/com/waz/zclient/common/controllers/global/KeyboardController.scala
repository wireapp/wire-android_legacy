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
package com.waz.zclient.common.controllers.global

import android.app.Activity
import android.graphics.Rect
import android.view.{View, ViewTreeObserver}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, WireContext}

class KeyboardController(implicit inj: Injector, cxt: WireContext, ec: EventContext) extends ViewTreeObserver.OnGlobalLayoutListener with Injectable {

  val isKeyboardVisible = Signal(false)
  isKeyboardVisible(v => verbose(s"Keyboard visible: $v"))

  val keyboardHeight = Signal(0)

  private val rootLayout = cxt match {
    case c: Activity => Some(c.getWindow.getDecorView.findViewById(android.R.id.content).asInstanceOf[View])
    case _ => None
  }

  override def onGlobalLayout() = rootLayout.foreach { rootLayout =>
      val statusAndNavigationBarHeight = getNavigationBarHeight(rootLayout.getContext) + getStatusBarHeight(rootLayout.getContext)

      val r = new Rect
      rootLayout.getWindowVisibleDisplayFrame(r)
      val screenHeight: Int = rootLayout.getRootView.getHeight
      val kbHeight = screenHeight - r.bottom - statusAndNavigationBarHeight

      isKeyboardVisible ! (kbHeight > 0)
      keyboardHeight ! kbHeight
  }

  def isVisible = isKeyboardVisible.currentValue.getOrElse(false)

  //Returns true if keyboard state was changed
  def hideKeyboardIfVisible(): Boolean = {
    if (isVisible) {
      KeyboardUtils.hideKeyboard(inject[Activity])
      true
    } else false
  }

  //Returns true if keyboard state was changed
  def showKeyboardIfHidden(): Boolean = {
    if (!isVisible) {
      KeyboardUtils.showKeyboard(inject[Activity])
      true
    } else false

  }

  rootLayout.foreach (rootLayout => rootLayout.getViewTreeObserver.addOnGlobalLayoutListener(this))
}

