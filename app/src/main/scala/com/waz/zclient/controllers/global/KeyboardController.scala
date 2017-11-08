/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.controllers.global

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.{View, ViewTreeObserver}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ViewUtils

import scala.concurrent.Future
import scala.concurrent.duration._

class KeyboardController(implicit inj: Injector) extends ViewTreeObserver.OnGlobalLayoutListener with Injectable {
  import KeyboardController._
  import com.waz.threading.Threading.Implicits.Ui

  private val cxt = inject[Context]

  val keyboardVisibility = Signal(false)
  val keyboardHeight = Signal(0)

  private val rootLayout = cxt match {
    case c: Activity => Some(c.getWindow.getDecorView.findViewById(android.R.id.content).asInstanceOf[View])
    case _ => None
  }

  override def onGlobalLayout() = rootLayout.foreach { rootLayout =>
      val statusAndNavigationBarHeight = ViewUtils.getNavigationBarHeight(rootLayout.getContext) + ViewUtils.getStatusBarHeight(rootLayout.getContext)

      val r = new Rect
      rootLayout.getWindowVisibleDisplayFrame(r)
      val screenHeight: Int = rootLayout.getRootView.getHeight
      val kbHeight = screenHeight - r.bottom - statusAndNavigationBarHeight

      keyboardVisibility ! (kbHeight > 0)
      keyboardHeight ! kbHeight
  }

  def hideKeyboardIfVisible(): Future[Unit] =
    if (KeyboardUtils.isKeyboardVisible(cxt)) {
      KeyboardUtils.hideKeyboard(inject[Activity])
      CancellableFuture.delayed(HideDelay) {}
    } else
      Future.successful(())

  rootLayout.foreach (rootLayout => rootLayout.getViewTreeObserver.addOnGlobalLayoutListener(this))
}

object KeyboardController {
  private val HideDelay = 200.millis
}
