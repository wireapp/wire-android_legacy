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

import scala.concurrent.{ExecutionContext, Future}

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

  sealed trait ZError extends Throwable {
    val description: String
    val cause: Option[Throwable]
  }

  case class UnexpectedError(causeError: Throwable) extends ZError {
    override val description: String = causeError.getMessage
    override val cause: Option[Throwable] = Some(causeError)
  }

  sealed trait NotFound extends ZError
  case class NotFoundRemote(description: String, cause: Option[Throwable] = None) extends NotFound
  case class NotFoundLocal(description: String, cause: Option[Throwable] = None) extends NotFound

  case class NetworkError(errorResponse: ErrorResponse) extends ZError {
    override val description: String = errorResponse.toString
    override val cause: Option[Throwable] = None
  }

  sealed trait LogicError extends ZError
  case class ValidationError(description: String, cause: Option[Throwable] = None) extends LogicError



}
