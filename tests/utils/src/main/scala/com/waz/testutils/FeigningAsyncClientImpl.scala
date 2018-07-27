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
package com.waz.testutils

import com.waz.threading.CancellableFuture
import com.waz.znet._

//class FeigningAsyncClientImpl extends AsyncClientImpl(wrapper = TestClientWrapper()) {
//  @volatile var simulateNetworkFailure = false
//
//  override def apply(request: Request[_]): CancellableFuture[Response] =
//    if (simulateNetworkFailure) CancellableFuture.successful(Response(Response.ConnectionError("somebody set up us the bomb")))
//    else super.apply(request)
//
//}
