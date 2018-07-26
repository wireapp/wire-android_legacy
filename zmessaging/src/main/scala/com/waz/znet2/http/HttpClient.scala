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
package com.waz.znet2.http

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.error
import com.waz.threading.CancellableFuture
import com.waz.znet2.http.HttpClient._
import com.waz.znet2.http.Request.QueryParameter

import scala.concurrent.ExecutionContext

object HttpClient {

  type ProgressCallback = Progress => Unit

  case class Progress(progress: Long, total: Option[Long]) {
    val isCompleted: Boolean = total.forall(_ == progress)
  }

  case class ErrorResponse(response: Response[Body]) extends Throwable

  sealed trait HttpClientError                                            extends Throwable
  case class EncodingError(err: Throwable)                                extends HttpClientError
  case class DecodingError(err: Throwable, response: Response[EmptyBody]) extends HttpClientError
  case class ConnectionError(err: Throwable)                              extends HttpClientError
  case class UnknownError(err: Throwable)                                 extends HttpClientError

  trait CustomErrorConstructor[E] {
    def constructFrom(error: HttpClientError): E
  }

  object dsl {

    /**
      * Just convenient method for the case when query parameter is optional
      * This can be refactored after dotty will be released http://dotty.epfl.ch/docs/reference/union-types.html
      */
    def queryParameters(params: (String, Any)*): List[QueryParameter] =
      params.toList.flatMap {
        case (name, value) =>
          value match {
            case optionalValue: Option[Any] =>
              optionalValue.map(_.toString).map(name -> _).map(List(_)).getOrElse(List.empty)
            case simpleValue: Any =>
              List(name -> simpleValue.toString)
          }
      }

    implicit class RichRequest[T: RequestSerializer](private[http] val request: Request[T]) {
      def withUploadCallback(callback: ProgressCallback): PreparingRequest[T] =
        withUploadCallback(Some(callback))

      def withUploadCallback(callback: Option[ProgressCallback]): PreparingRequest[T] =
        new PreparingRequest[T](request, callback, None)

      def withDownloadCallback(callback: ProgressCallback): PreparingRequest[T] =
        withDownloadCallback(Some(callback))

      def withDownloadCallback(callback: Option[ProgressCallback]): PreparingRequest[T] =
        new PreparingRequest[T](request, None, callback)

      def withResultHttpCodes(codes: Set[Int]): PreparingRequest[T] =
        new PreparingRequest[T](request, None, None, codes)

      def withResultType[R: ResponseDeserializer]: PreparedRequest[T, R] =
        new PreparedRequest[T, R](request, None, None)
    }

    class PreparingRequest[T](
        private[http] val request: Request[T],
        private[http] val uploadCallback: Option[ProgressCallback] = None,
        private[http] val downloadCallback: Option[ProgressCallback] = None,
        private[http] val resultResponseCodes: Set[Int] = ResponseCode.SuccessCodes
    )(implicit
      requestSerializer: RequestSerializer[T]) {

      def withUploadCallback(callback: ProgressCallback): PreparingRequest[T] =
        new PreparingRequest[T](request, Some(callback), downloadCallback)

      def withUploadCallback(callback: Option[ProgressCallback]): PreparingRequest[T] =
        callback.fold(this)(c => new PreparingRequest[T](request, Some(c), downloadCallback))

      def withDownloadCallback(callback: ProgressCallback): PreparingRequest[T] =
        new PreparingRequest[T](request, uploadCallback, Some(callback))

      def withDownloadCallback(callback: Option[ProgressCallback]): PreparingRequest[T] =
        callback.fold(this)(c => new PreparingRequest[T](request, uploadCallback, Some(c)))

      def withResultHttpCodes(codes: Set[Int]): PreparingRequest[T] =
        new PreparingRequest[T](request, uploadCallback, downloadCallback, codes)

      def withResultType[R: ResponseDeserializer]: PreparedRequest[T, R] =
        new PreparedRequest[T, R](request, uploadCallback, downloadCallback, resultResponseCodes)

    }

    class PreparedRequest[T, R](
        private[http] val request: Request[T],
        private[http] val uploadCallback: Option[ProgressCallback] = None,
        private[http] val downloadCallback: Option[ProgressCallback] = None,
        private[http] val resultResponseCodes: Set[Int] = ResponseCode.SuccessCodes
    )(implicit
      rs: RequestSerializer[T],
      rd: ResponseDeserializer[R]) {

      def execute(implicit client: HttpClient): CancellableFuture[R] =
        client.result(request, uploadCallback, downloadCallback)

      def withErrorType[E: ResponseDeserializer]: PreparedRequestWithErrorType[T, R, E] =
        new PreparedRequestWithErrorType[T, R, E](request, uploadCallback, downloadCallback, resultResponseCodes)

    }

    class PreparedRequestWithErrorType[T, R, E](
        private[http] val request: Request[T],
        private[http] val uploadCallback: Option[ProgressCallback] = None,
        private[http] val downloadCallback: Option[ProgressCallback] = None,
        private[http] val resultResponseCodes: Set[Int]
    )(implicit
      rs: RequestSerializer[T],
      rd: ResponseDeserializer[R],
      erd: ResponseDeserializer[E]) {

      def executeSafe(implicit client: HttpClient, c: CustomErrorConstructor[E]): CancellableFuture[Either[E, R]] =
        client.resultWithDecodedErrorSafe[T, E, R](request, uploadCallback, downloadCallback, resultResponseCodes)

      def executeSafe[TR](resultTransformer: R => TR)(
          implicit
          client: HttpClient,
          c: CustomErrorConstructor[E],
          ex: ExecutionContext
      ): CancellableFuture[Either[E, TR]] =
        client
          .resultWithDecodedErrorSafe[T, E, R](request, uploadCallback, downloadCallback, resultResponseCodes)
          .map(_.right.map(resultTransformer))

      def execute(implicit client: HttpClient, ev: E <:< Throwable): CancellableFuture[R] =
        client.resultWithDecodedError[T, E, R](request, uploadCallback, downloadCallback, resultResponseCodes)

    }

  }

  object AutoDerivation extends AutoDerivationRulesForDeserializers with AutoDerivationRulesForSerializers

}

trait HttpClient {

  protected implicit val ec: ExecutionContext

  protected def executeIgnoreInterceptor(
      request: Request[Body],
      uploadCallback: Option[ProgressCallback],
      downloadCallback: Option[ProgressCallback]
  ): CancellableFuture[Response[Body]]

  def execute(
      request: Request[Body],
      uploadCallback: Option[ProgressCallback],
      downloadCallback: Option[ProgressCallback]
  ): CancellableFuture[Response[Body]] = {
    request.interceptor.intercept(request)
      .flatMap(executeIgnoreInterceptor(_, uploadCallback, downloadCallback))
      .flatMap(request.interceptor.intercept(request, uploadCallback, downloadCallback, _))
  }

  private def serializeRequest[T](
      request: Request[T]
  )(implicit rs: RequestSerializer[T]): CancellableFuture[Request[Body]] =
    CancellableFuture(rs.serialize(request)).recoverWith {
      case err =>
        error("Error while serializing request.", err)
        CancellableFuture.failed(EncodingError(err))
    }

  private def deserializeResponse[T](
      response: Response[Body]
  )(implicit rd: ResponseDeserializer[T]): CancellableFuture[T] =
    CancellableFuture(rd.deserialize(response)).recoverWith {
      case err =>
        error("Error while deserializing response.", err)
        //we already have tried to deserialize response body, so it may be broken.
        CancellableFuture.failed(DecodingError(err, response.copy(body = EmptyBodyImpl)))
    }

  def result[T: RequestSerializer, R: ResponseDeserializer](
      request: Request[T],
      uploadCallback: Option[ProgressCallback] = None,
      downloadCallback: Option[ProgressCallback] = None,
      resultResponseCodes: Set[Int] = ResponseCode.SuccessCodes
  ): CancellableFuture[R] =
    serializeRequest(request)
      .flatMap(execute(_, uploadCallback, downloadCallback))
      .flatMap { response =>
        if (resultResponseCodes.contains(response.code)) deserializeResponse[R](response)
        else CancellableFuture.failed(ErrorResponse(response))
      }
      .recoverWith {
        case err: HttpClientError => CancellableFuture.failed(err)
        case err =>
          error("Unexpected error.", err)
          CancellableFuture.failed(UnknownError(err))
      }

  def resultWithDecodedError[T, E, R](
      request: Request[T],
      uploadCallback: Option[ProgressCallback] = None,
      downloadCallback: Option[ProgressCallback] = None,
      resultResponseCodes: Set[Int] = ResponseCode.SuccessCodes
  )(implicit
    rs: RequestSerializer[T],
    erd: ResponseDeserializer[E],
    ev: E <:< Throwable,
    rd: ResponseDeserializer[R]): CancellableFuture[R] =
    serializeRequest(request)
      .flatMap(execute(_, uploadCallback, downloadCallback))
      .flatMap { response =>
        if (resultResponseCodes.contains(response.code)) deserializeResponse[R](response)
        else deserializeResponse[E](response).flatMap(err => CancellableFuture.failed(err))
      }
      .recoverWith {
        case err: HttpClientError => CancellableFuture.failed(err)
        case err =>
          error("Unexpected error.", err)
          CancellableFuture.failed(UnknownError(err))
      }

  def resultWithDecodedErrorSafe[T: RequestSerializer,
                                 E: ResponseDeserializer: CustomErrorConstructor,
                                 R: ResponseDeserializer](
      request: Request[T],
      uploadCallback: Option[ProgressCallback] = None,
      downloadCallback: Option[ProgressCallback] = None,
      resultResponseCodes: Set[Int] = ResponseCode.SuccessCodes
  ): CancellableFuture[Either[E, R]] =
    serializeRequest(request)
      .flatMap(execute(_, uploadCallback, downloadCallback))
      .flatMap { response =>
        if (resultResponseCodes.contains(response.code)) deserializeResponse[R](response).map(Right.apply)
        else deserializeResponse[E](response).map(Left.apply)
      }
      .recover {
        case err: HttpClientError => Left(implicitly[CustomErrorConstructor[E]].constructFrom(err))
        case err =>
          error("Unexpected error.", err)
          Left(implicitly[CustomErrorConstructor[E]].constructFrom(UnknownError(err)))
      }

}
