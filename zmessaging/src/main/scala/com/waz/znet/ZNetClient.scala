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
package com.waz.znet

import java.util.concurrent.atomic.AtomicInteger

import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.service.BackendConfig
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.wrappers.URI
import com.waz.znet.Response._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
 * Network connection component, handles authentication, webSocket, and retrying.
 *
 * TODO: register Android network state listener, so we would know if device has network connection,
 * there is no point in retrying if know that there is no usable network,
 * and it would be good to retry connections as soon as we are back online
 */

trait ZNetClient {
  import ZNetClient._

  def apply[A](r: Request[A]): CancellableFuture[Response]
  def updateWithErrorHandling[A](name: String, r: Request[A])(implicit ec: ExecutionContext): ErrorOrResponse[Unit]
  def withErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, T])(implicit ec: ExecutionContext): ErrorOrResponse[T]
  def withFutureErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, T])(implicit ec: ExecutionContext): ErrorOr[T]
  def chainedWithErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, ErrorOrResponse[T]])(implicit ec: ExecutionContext): ErrorOrResponse[T]
  def chainedFutureWithErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, ErrorOr[T]])(implicit ec: ExecutionContext): ErrorOr[T]
  def close(): Future[Unit]
  def client: AsyncClient
}

class ZNetClientImpl(auth: Option[AuthenticationManager] = None, override val client: AsyncClient, baseUri: URI) extends ZNetClient {

  import ZNetClient._
  private implicit val logTag: LogTag = logTagFor[ZNetClient]
  private implicit val dispatcher = new SerialDispatchQueue(Threading.ThreadPool)

  private val queue = new mutable.Queue[RequestHandle]()
  private val ongoing = new mutable.HashMap[Int, RequestHandle]()
  private val ongoingLongRunning = new mutable.HashMap[Int, RequestHandle]()
  private var closed = false

  @volatile private var ema20: Float = 0.0f // exponential moving average of 20 (for request-response time metrics)

  /**
   * Max allowed number of simultaneous http operations.
   * It's not practical to execute too many concurrent operations, it would only slow everything down.
   * This number could be somehow computed from connection quality and/or kind of device,
   * on powerful device with good connection it could be beneficial to increase this count.
   *
   * TODO: should we allow to cross this queryLimit for important requests?
   */
  def MaxConcurrentRequests = 4
  def LongRunning = 45.seconds

  override def apply[A](r: Request[A]): CancellableFuture[Response] = {
    val handle = new RequestHandle(r)
    enqueue(handle)
    new CancellableFuture[Response](handle.promise) {
      override def cancel()(implicit tag: LogTag): Boolean = {
        handle.cancelled = true
        handle.httpFuture.foreach(_.cancel()(tag))
        super.cancel()(tag)
      }
    }
  }

  override def close(): Future[Unit] = Future {
    closed = true
    queue.foreach(_.promise.success(Response(Response.Cancelled)))
    queue.clear()
//    auth.foreach(_.close())

    ongoing.values.toList foreach cancel
  }

  override def updateWithErrorHandling[A](name: String, r: Request[A])(implicit ec: ExecutionContext): ErrorOrResponse[Unit] =
    withErrorHandling(name, r) { case Response(SuccessHttpStatus(), _, _) => () } (ec)

  override def withErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, T])(implicit ec: ExecutionContext): ErrorOrResponse[T] =
    apply(r).map(pf.andThen(Right(_)).orElse(errorHandling(name)))(ec)


  override def chainedWithErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, ErrorOrResponse[T]])(implicit ec: ExecutionContext): ErrorOrResponse[T] =
    apply(r).flatMap(pf.orElse(errorHandling(name) andThen CancellableFuture.successful))(ec)


  override def withFutureErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, T])(implicit ec: ExecutionContext): ErrorOr[T] =
    apply(r).future.map(pf.andThen(Right(_)).orElse(errorHandling(name))).recover { case NonFatal(e) => Left(ErrorResponse.internalError("future failed: " + e.getMessage)) }


  override def chainedFutureWithErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, ErrorOr[T]])(implicit ec: ExecutionContext): ErrorOr[T] =
    apply(r).future.flatMap(pf.orElse(errorHandling(name) andThen Future.successful))(ec).recover { case NonFatal(e) => Left(ErrorResponse.internalError("future failed: " + e.getMessage)) }

  private def dispatch(): Unit = {
    if (ongoing.size < MaxConcurrentRequests) {
      queue.dropWhile(_.cancelled)
      if (queue.nonEmpty) {
        val handle = queue.dequeue()
        handle.startTime = System.currentTimeMillis()
        ongoing += handle.id -> handle

        val request = handle.request.withBaseUriIfNone(baseUri)

        val future =
          if (request.requiresAuthentication) {
            auth match {
              case Some(am) => CancellableFuture.lift(am.currentToken()) flatMap {
                case Right(token) => client(request.withHeaders(token.headers))
                case Left(error) => CancellableFuture.successful(Response(HttpStatus(error.code, error.message)))
                //TODO it would be good to merge ErrorResponse and Response under one trait (https://github.com/wireapp/android-project/issues/55)
              }
              case _ => CancellableFuture.failed(throw new Exception("Attempted to perform request that requires authentication using a non-authenticated global ZNetClient"))
            }
          } else client(request)

        handle.httpFuture = Some(future)

        future.onComplete {
          case Success(resp @ Response(HttpStatus(Status.Unauthorized, _), _, _)) => onAuthorizationFailed(handle, resp)
          case Success(resp @ Response(status @ ErrorStatus(), _, _)) if handle.shouldRetry(status) =>
            debug(s"Got error response: '$resp' for request: '$request'")
            retry(handle, status.status == Status.RateLimiting)
          case Success(response)  => done(handle, response)
          case Failure(exc: CancelException) => done(handle, Response(Cancelled))
          case Failure(exc) => done(handle, Response(Response.InternalError(exc.getMessage, Some(exc))))
        }
      }
    } else {
      // XXX: this shouldn't be needed in practice, only for debugging
      val time = System.currentTimeMillis()
      val longRunning = ongoing.values filter (_.startTime + LongRunning.toMillis < time)
      if (longRunning.nonEmpty) {
        error(s"Some requests are running for over $LongRunning, will remove them from ongoing: $longRunning")
        ongoingLongRunning ++= longRunning.map(r => r.id -> r)
        longRunning.foreach(h => ongoing.remove(h.id))
        dispatch()
      }
    }
  }

  private def onAuthorizationFailed(handle: RequestHandle, response: Response) = {
    val request = handle.request
    if (!request.requiresAuthentication) {
      // if client didn't request authentication then we will just return the error response
      error(s"Client didn't require authentication, but server returned 401 response. request: $request, response: $response")
      done(handle, response)
    } else if (handle.authRetry) {
      error(s"Received AuthorizationError: $response for $request after retry")
      done(handle, response)
    } else {
      info(s"Received AuthorizationError: $response for $request, will invalidate token and try again")
      auth.foreach(_.invalidateToken() map { _ =>
        handle.authRetry = true
        enqueue(handle)
      })
    }
  }

  private def retry(handle: RequestHandle, shouldBackOff: Boolean) = {
    val delay = handle.retryDelay(rateLimited = shouldBackOff)
    debug(s"Retrying request: '${handle.request}', retry: ${handle.retry}, delay: $delay, ongoing: ${ongoing.size}")
    val removed = ongoing.remove(handle.id)
    if (removed.isEmpty) {
      val longRunningRemoved = ongoingLongRunning.remove(handle.id)
      if (longRunningRemoved.isEmpty) error(s"retry() - handle '$handle' should have been removed from ongoing $ongoing or ongoingLongRunning $ongoingLongRunning", new IllegalStateException("handle not removed"))
      else debug(s"retrying long running task. remaining: ${ongoingLongRunning.size}")
    }

    handle.retry += 1
    CancellableFuture.delay(delay) onComplete { _ => if (!handle.cancelled) enqueue(handle) }
  }

  private def enqueue(handle: RequestHandle) = dispatcher {
    queue += handle
    dispatch()
  } onFailure {
    case e => handle.promise.failure(e)
  }

  private def cancel(handle: RequestHandle) = dispatcher {
    if (!handle.promise.isCompleted) {
      handle.cancelled = true
      handle.httpFuture.foreach(_.cancel())
      done(handle, Response(Response.Cancelled))
    }
  }

  private def done(handle: RequestHandle, response: Response): Unit = {
    val latest = System.currentTimeMillis - handle.startTime
    ema20 = if (ema20 == 0.0f) latest.toFloat else ema20 * 0.95f + latest.toFloat * 0.05f
    verbose(f"request time (ema20): $ema20%5.0f ms, latest: $latest%5d ${handle.request.httpMethod} ${handle.request.resourcePath getOrElse "???"} (id ${response.headers("request-id").getOrElse("???")})")
    debug(s"done request: '${handle.request}', response: $response, ongoing: ${ongoing.size}, promise completed: ${handle.promise.isCompleted}")
    val removed = ongoing.remove(handle.id)
    if (removed.isEmpty && !handle.promise.isCompleted) {
      val longRunningRemoved = ongoingLongRunning.remove(handle.id)
      if (longRunningRemoved.isEmpty) error(s"done($response) - handle '$handle' should have been removed from ongoing $ongoing or ongoingLongRunning $ongoingLongRunning", new IllegalStateException("handle not removed"))
      else debug(s"long running task done. remaining: ${ongoingLongRunning.size}")
    }

    handle.promise.trySuccess(response)
    dispatch()
  }
}

object ZNetClient {

  private val handleId = new AtomicInteger(0)
  def nextId = handleId.incrementAndGet()

  class EmptyAsyncClientImpl(client: Future[ClientWrapper] = ClientWrapper()) extends AsyncClientImpl(wrapper = client) {
    override def apply(request: Request[_]): CancellableFuture[Response] =
      CancellableFuture.failed(new Exception("Empty async client"))
  }

  class EmptyClient extends ZNetClientImpl(None, new EmptyAsyncClientImpl(), BackendConfig.StagingBackend.baseUrl) {
    override def apply[A](r: Request[A]): CancellableFuture[Response] = CancellableFuture.failed(new Exception("Empty client"))
  }

  def errorHandling[T](name: String)(implicit logTag: LogTag): PartialFunction[Response, Either[ErrorResponse, T]] = {
    case Response(Cancelled, _, _) =>
      Left(ErrorResponse(ErrorResponse.CancelledCode, s"cancelled request: '$name'", "cancelled"))
    case Response(InternalError(_, Some(e: CancelException), _), _, _) =>
      Left(ErrorResponse(ErrorResponse.CancelledCode, s"cancelled request: '$name', ex: ${e.getMessage}", "cancelled"))
    case Response(InternalError(_, Some(e: TimeoutException), _), _, _) =>
      Left(ErrorResponse(ErrorResponse.TimeoutCode, s"request $name timed out, ex: ${e.getMessage}", "timeout"))
    case Response(ConnectionError(msg), _, _) =>
      Left(ErrorResponse(ErrorResponse.ConnectionErrorCode, s"request '$name' connection failed: $msg", "connection-error"))
    case Response(ErrorStatus(), ErrorResponse(code, msg, label), _) =>
      warn(s"$name response to $name query: ${ErrorResponse(code, msg, label)}")
      Left(ErrorResponse(code, msg, label))
    case resp @ Response(ErrorStatus(), body, headers) =>
      warn(s"Error $name query failed: $resp, body: $body, headers: $headers")
      Left(ErrorResponse(resp.status.status, resp.toString, "internal-error"))
    case resp @ Response(_, _, _) =>
      error(s"Unexpected response to $name query: $resp")
      Left(ErrorResponse.internalError(s"unexpected response to $name: $resp"))
  }

  class RequestHandle( val request: Request[_],
                       val id: Int = nextId,
                       val promise: Promise[Response] = Promise[Response](),
                       var authRetry: Boolean = false,  // will be set to true if this request is being retried due to authorization error
                       var cancelled: Boolean = false,
                       var startTime: Long = 0,
                       var retry: Int = 0,
                       var httpFuture: Option[CancellableFuture[Response]] = None) {

    def retryPolicy = request.retryPolicy

    def shouldRetry(status: Status) = retryPolicy.shouldRetry(status, retry)

    def retryDelay(rateLimited: Boolean) = retryPolicy.backoff.delay(retry, if (rateLimited) 5.seconds else Duration.Zero)

    override def toString: String = s"RequestHandle($id, $request, completed: ${promise.isCompleted}, cancelled: $cancelled, retry: $retry)"
  }

  type ErrorOrResponse[T] = CancellableFuture[Either[ErrorResponse, T]]
  type ErrorOr[A] = Future[Either[ErrorResponse, A]]
}
