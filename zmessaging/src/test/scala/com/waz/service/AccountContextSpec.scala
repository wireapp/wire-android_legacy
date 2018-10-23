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

import com.waz.service.AccountsService.{InForeground, LoggedOut}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading
import com.waz.utils.events.Signal

class AccountContextSpec extends AndroidFreeSpec {

  scenario("Logged out accounts should not receive events") {

    accountStates ! Map.empty

    val signal = Signal(0)
    var updates = 0

    signal.on(Threading.Background) { _ =>
      updates += 1
    } (accountContext)

    signal ! 1

    awaitAllTasks
    updates shouldEqual 0 //one update on registering subscription, one on update
  }

  //FIXME - flaky test when run in full suite
  ignore("Logged in account should have enabled event context") {

    val signal = Signal(0)
    var updates = 0

    signal.on(Threading.Background) { _ =>
      updates += 1
    } (accountContext)

    signal ! 1

    awaitAllTasks
    updates shouldEqual 2 //one update on registering subscription, one on update
  }

  scenario("After logging out, account should have not receive more events. Logging back in should reactive event context") {

    val signal = Signal(0)
    var updates = 0

    signal.on(Threading.Background) { _ =>
      updates += 1
    } (accountContext)

    updateAccountState(account1Id, LoggedOut)

    awaitAllTasks //let context stop

    signal ! 1

    awaitAllTasks //let signal propagate, if there's a bug

    updates shouldEqual 1 //one update on registering subscription before event context was disabled

    updateAccountState(account1Id, InForeground)

    awaitAllTasks //let context restart

    updates shouldEqual 2 //one update on registering subscription before event context was disabled, one for registering again once context was re-activated
  }
}
