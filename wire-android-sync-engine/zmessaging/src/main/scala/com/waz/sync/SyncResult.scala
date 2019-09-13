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
package com.waz.sync

import com.waz.api.impl.ErrorResponse

sealed trait SyncResult

object SyncResult {

  case object Success extends SyncResult

  case class Failure(error: ErrorResponse) extends SyncResult
  object Failure {
    def apply(msg: String = ""): Failure =
      Failure(ErrorResponse.internalError(msg))
  }

  case class Retry(error: ErrorResponse) extends SyncResult
  object Retry {
    def apply(msg: String = ""): Retry =
      new Retry(ErrorResponse(ErrorResponse.RetryCode, msg, "internal-error-retry"))
  }

  def apply(error: ErrorResponse): SyncResult =
    if (!error.isFatal) Retry(error) else Failure(error)

  //TODO this loses important information about the exception - would be better if ErrorResponse extended Throwable/Exception
  def apply(e: Throwable): SyncResult =
    Failure(ErrorResponse.internalError(e.getMessage))

  def apply(result: Either[ErrorResponse, _]): SyncResult =
    result.fold[SyncResult](SyncResult(_), _ => SyncResult.Success)
}
