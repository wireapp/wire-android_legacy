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
package com.waz.zclient

import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.views.LoadingIndicatorView._

class SpinnerController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable {

  val spinnerShowing: SourceSignal[Option[AnimationType]] = Signal(None)

  def showSpinner(animationType: AnimationType = Spinner): Unit = spinnerShowing ! Some(animationType)

  def hideSpinner(): Unit = spinnerShowing ! None

  def showSpinner(show: Boolean): Unit = spinnerShowing ! (if (show) Some(Spinner) else None)
  def showDimmedSpinner(show: Boolean, text: String = ""): Unit = spinnerShowing ! (if (show) Some(SpinnerWithDimmedBackground(text)) else None)
}
