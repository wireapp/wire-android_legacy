/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.model.UserId
import com.waz.service.AccountsService.LoggedOut
import com.waz.service.ZMessaging.accountTag
import com.wire.signals.{BaseEventContext, SerialDispatchQueue}

final class AccountContext(userId: UserId, accounts: AccountsService) extends BaseEventContext {
  implicit val logTag: LogTag = accountTag[AccountContext](userId)
  private implicit val dispatcher = SerialDispatchQueue(name = "AccountContext")

  accounts.accountState(userId).on(dispatcher) {
    case LoggedOut =>
      stop()
      verbose(l"Account context stopped")
    case _ =>
      start()
      verbose(l"Account context started")
  }
}
