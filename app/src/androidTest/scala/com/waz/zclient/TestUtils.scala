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

import java.net.URI

import android.content.Context

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionException, Future}
import scala.util.{Failure, Success, Try}

object TestUtils {

  val defaultTestTimeout: FiniteDuration = 3.minutes

  def asyncTest(body: Future[_]): Unit = {
    Try(Await.result(body, defaultTestTimeout)) match {
      case Success(_) =>
      case Failure(err: ExecutionException) => throw err.getCause
      case Failure(err) => throw err
    }
  }

  def getResourceUri(context: Context, resourceId: Int): URI = {
    URI.create(s"android.resource://${context.getPackageName}/$resourceId")
  }

}
