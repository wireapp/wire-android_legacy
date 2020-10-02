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

import com.wire.signals.{Signal, SourceSignal}
import com.waz.zclient.SpinnerController.{Hide, Show, SpinnerParameters}
import com.waz.zclient.views.LoadingIndicatorView._

class SpinnerController(implicit inj: Injector, cxt: WireContext) extends Injectable {

  val spinnerShowing: SourceSignal[SpinnerParameters] = Signal(Hide())

  def showSpinner(animationType: AnimationType = Spinner, forcedIsDarkTheme: Option[Boolean] = None): Unit = spinnerShowing ! Show(animationType, forcedIsDarkTheme)

  def hideSpinner(message: Option[String] = None): Unit = spinnerShowing ! Hide(message)

  def showSpinner(show: Boolean): Unit = spinnerShowing ! (if (show) Show(Spinner) else Hide(None))
  def showDimmedSpinner(show: Boolean, text: String = ""): Unit = spinnerShowing ! (if (show) Show(SpinnerWithDimmedBackground(text)) else Hide(None))
}

object SpinnerController {
  sealed trait SpinnerParameters
  case class Show(animationType: AnimationType, forcedIsDarkTheme: Option[Boolean] = None) extends SpinnerParameters
  case class Hide(message: Option[String] = None) extends SpinnerParameters
}
