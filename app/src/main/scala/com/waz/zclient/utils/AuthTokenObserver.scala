/**
  * Wire
  * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.utils

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountManager
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.user.data.source.remote.AuthHeaderInterceptor
import com.waz.zclient.{Injectable, Injector}

class AuthTokenObserver(implicit injector: Injector, ec: EventContext) extends Injectable with DerivedLogTag {

  private lazy val accountManager = inject[Signal[AccountManager]]

  private val authToken = accountManager.flatMap(_.accessToken)

  //TODO: might observe accessToken and tokenType separately
  authToken.on(Threading.Background) {
    case Some(x) =>
      AuthHeaderInterceptor.setToken(x.accessToken)
      AuthHeaderInterceptor.setTokenType(x.tokenType)
    case None => AuthHeaderInterceptor.setToken(null)
  }

}
