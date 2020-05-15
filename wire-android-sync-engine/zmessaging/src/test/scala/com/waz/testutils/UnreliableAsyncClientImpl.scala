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

import scala.concurrent.duration._
import scala.util.matching.Regex

//class UnreliableAsyncClientImpl extends AsyncClientImpl(wrapper = TestClientWrapper()) {
//  @volatile var delayInMillis: Long = 200L
//  @volatile var failFor: Option[(Regex, String)] = None
//
//  override def apply(request: Request[_]): CancellableFuture[Response] = {
//    CancellableFuture.delay(delayInMillis.millis) flatMap { _ =>
//      val fail = failFor exists { failFor =>
//        val (uriRegex, failingMethod) = failFor
//        failingMethod == request.httpMethod && uriRegex.pattern.matcher(request.absoluteUri.toString).matches
//      }
//      if (fail) CancellableFuture.successful(Response(Response.HttpStatus(500)))
//      else super.apply(request)
//    }
//  }
//}
