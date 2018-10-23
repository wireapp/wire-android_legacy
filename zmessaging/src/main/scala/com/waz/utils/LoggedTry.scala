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
package com.waz.utils

import com.waz.ZLog._
import com.waz.service.tracking.TrackingService.{NoReporting, exception}
import com.waz.{ZLog, utils}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object LoggedTry {
  def apply[A](f: => A)(implicit tag: LogTag): Try[A] = try Success(f) catch errorHandler()

  def local[A](f: => A)(implicit tag: LogTag): Try[A] = try Success(f) catch errorHandler()

  def errorHandler[A]()(implicit tag: LogTag): PartialFunction[Throwable, Try[A]] = {
    case NonFatal(e) =>
      ZLog.warn("logged try failed", e)
      if (utils.isTest ) exception(e, "logged try failed (non-fatal)")
      Failure(e)
    case e: Throwable =>
      ZLog.error("logged try got fatal error", e)
      if (utils.isTest) exception(e, "logged try failed (fatal)")
      Failure(BoxedError(e))
  }
}

case class BoxedError(cause: Throwable) extends RuntimeException("BoxedError", cause) with NoReporting

object BoxedError {

  def boxFatal[A](body: => A) = try {
    body
  } catch {
    case NonFatal(e) => throw e
    case e: Throwable => throw new BoxedError(e)
  }

  def boxOoM[A](body: => A) = try {
    body
  } catch {
    case e: OutOfMemoryError => throw new BoxedError(e)
  }
}
