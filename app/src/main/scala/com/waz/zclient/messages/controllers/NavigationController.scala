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
package com.waz.zclient.messages.controllers

import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page}
import com.waz.zclient.{Injectable, Injector}

class NavigationController(implicit injector: Injector) extends  Injectable {

  val visiblePage = Signal[Page]()

  val mainActivityActive: SourceSignal[Int] = Signal(0)

  inject[INavigationController].addNavigationControllerObserver(new NavigationControllerObserver {
    override def onPageVisible(page: Page): Unit = visiblePage ! page
  })

}
