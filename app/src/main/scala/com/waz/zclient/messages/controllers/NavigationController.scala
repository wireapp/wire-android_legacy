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
package com.waz.zclient.messages.controllers

import com.waz.utils.events.Signal
import com.waz.zclient.controllers.navigation.{Page, NavigationControllerObserver, INavigationController}
import com.waz.zclient.{Injector, Injectable}

class NavigationController(implicit injector: Injector) extends Injectable {

  val navController = inject[INavigationController]

  val messageStreamOpen = Signal[Boolean](false)

  navController.addNavigationControllerObserver(new NavigationControllerObserver {
    override def onPageStateHasChanged(page: Page): Unit = ()

    override def onPageVisible(page: Page): Unit = messageStreamOpen ! (page == Page.MESSAGE_STREAM)
  })

}
