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

sealed trait SyncResult {

  val isSuccess: Boolean

  // we should only retry if it makes sense (temporary network or server errors)
  // there is no point retrying requests which failed with 4xx status
  val shouldRetry: Boolean

  val error: Option[ErrorResponse]
}

object SyncResult {

  case object Success extends SyncResult {
    override val isSuccess = true
    override val shouldRetry = false
    override val error = None
  }

  case class Failure(error: Option[ErrorResponse], shouldRetry: Boolean = true) extends SyncResult {
    override val isSuccess = false
  }

  def apply(error: ErrorResponse): Failure =
    Failure(Some(error), !error.isFatal)

  //TODO this loses important information about the exception - would be better if ErrorResponse extended Throwable/Exception
  def apply(e: Throwable): SyncResult =
    Failure(Some(ErrorResponse.internalError(e.getMessage)), shouldRetry = false)

  def apply(result: Either[ErrorResponse, _]): SyncResult =
    result.fold[SyncResult](SyncResult(_), _ => SyncResult.Success)

  def retry(msg: String): SyncResult =
    Failure(Some(ErrorResponse(ErrorResponse.RetryCode, msg, "internal-error-retry")))

  def retry(): SyncResult =
    retry("")

  def failed(msg: String): SyncResult =
    Failure(Some(ErrorResponse.internalError(msg)), shouldRetry = false)

  def failed(): SyncResult =
    failed("")
}
