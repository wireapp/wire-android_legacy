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
package com.waz.zclient.common.controllers

import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.utils.UiStorage
import com.waz.zclient.{Injectable, Injector}

import scala.language.postfixOps

class SearchUserController(val toConv: Option[ConvId] = None)(implicit injector: Injector, ec: EventContext) extends Injectable {
  implicit private val uiStorage = inject[UiStorage]

  private val zms = inject[Signal[ZMessaging]]

  val selectedUsers = Signal(Seq.empty[UserId])
  val filter        = Signal("")

  val searchResults = for {
    z        <- zms
    selected <- selectedUsers
    filter   <- filter
    res      <- z.userSearch.search(filter, selected.toSet, toConv)
  } yield res

}
