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
package com.waz.model

import com.waz.api.impl.ErrorResponse
import com.waz.threading.CancellableFuture
import com.waz.log.LogSE.asSize

import scala.concurrent.{ ExecutionContext, Future }

object errors {

  implicit class FutureOps[T](val value: Future[T]) extends AnyVal {
    def toCancellable: CancellableFuture[T] = CancellableFuture.lift(value)
    def modelToEither(implicit ec: ExecutionContext): Future[Either[ZError, T]] =
      value.map(Right(_): Either[ZError, T]).recover { case err => Left(UnexpectedError(err)) }
    def eitherToModel[A](implicit ev: T =:= Either[ZError, A], ec: ExecutionContext): Future[A] =
      value.flatMap { either =>
        if (either.isLeft) Future.failed(either.left.get)
        else Future.successful(either.right.get)
      }
  }

  implicit class CancellableFutureOps[T](val value: CancellableFuture[T]) extends AnyVal {
    def modelToEither(implicit ec: ExecutionContext): CancellableFuture[Either[ZError, T]] =
      value.map(Right(_): Either[ZError, T]).recover { case err => Left(UnexpectedError(err)) }
    def eitherToModel[A](implicit ev: T =:= Either[ZError, A], ec: ExecutionContext): CancellableFuture[A] =
      value.flatMap { either =>
        if (either.isLeft) CancellableFuture.failed(either.left.get)
        else CancellableFuture.successful(either.right.get)
      }
  }

  abstract class ZError(val description: String, val cause: Option[Throwable])
      extends Throwable(description, cause.orNull)

  case class UnexpectedError(causeError: Throwable) extends ZError(causeError.getMessage, Some(causeError))

  abstract class NotFound(override val description: String, override val cause: Option[Throwable])
      extends ZError(description, cause)
  case class NotFoundRemote(override val description: String, override val cause: Option[Throwable] = None)
      extends NotFound(description, cause)
  case class NotFoundLocal(override val description: String, override val cause: Option[Throwable] = None)
      extends NotFound(description, cause)

  case class NetworkError(errorResponse: ErrorResponse) extends ZError(errorResponse.toString, None)

  abstract class LogicError(override val description: String, override val cause: Option[Throwable])
      extends ZError(description, cause)
  case class FailedExpectationsError(override val description: String, override val cause: Option[Throwable] = None)
    extends LogicError(description, cause)
  case class NotSupportedError(override val description: String, override val cause: Option[Throwable] = None)
    extends LogicError(description, cause)

  class ValidationError(override val description: String, override val cause: Option[Throwable] = None)
    extends LogicError(description, cause)

  case class AssetContentTooLargeError(contentSize: Long, allowedSize: Long)
    extends ValidationError(s"Asset content too large. Max allowed size: ${asSize(allowedSize)}. Actual size: ${asSize(contentSize)}", None)

  case class FileSystemError(override val description: String, override val cause: Option[Throwable] = None)
    extends ZError(description, cause)

  case class PermissionDeniedError(permissions: Seq[String]) extends
    ZError(s"Permissions list: ${permissions.mkString}", None)

}
