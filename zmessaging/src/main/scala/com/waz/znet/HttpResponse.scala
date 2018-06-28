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

import java.util.concurrent.atomic.AtomicLong

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api
import com.waz.api.impl.ProgressIndicator
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.wrappers.URI
import com.waz.znet.Request.ProgressCallback
import com.waz.znet.Response.{HttpStatus, ResponseBodyDecoder}
import com.waz.znet.ResponseConsumer.ConsumerState.Done
import com.koushikdutta.async.http._
import com.koushikdutta.async._
import com.koushikdutta.async.callback.CompletedCallback.NullCompletedCallback
import com.koushikdutta.async.callback.DataCallback.NullDataCallback
import com.koushikdutta.async.callback.{CompletedCallback, DataCallback}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._

trait HttpResponse {
  def status: Response.Status
  def body: ResponseContent
  def headers: Response.Headers

  def setDataCallback(callback: DataCallback): Unit = {}
  def setEndCallback(callback: CompletedCallback): Unit = {}

  def close(): Unit = {}
}

class HttpResponseImpl(val res: AsyncHttpResponse) extends HttpResponse {
  override val status: Response.Status = HttpStatus(res.code(), res.message())
  override val body: ResponseContent = EmptyResponse // TODO
  override val headers: Response.Headers = {
    val multiMap = res.headers().getMultiMap()
    val map: Map[String, String] = multiMap.keySet().asScala.toSet[String].map(k => (k -> multiMap.getString(k))).toMap
    Response.createHeaders(map)
  }

  override def setDataCallback(callback: DataCallback): Unit = res.setDataCallback(callback)
  override def setEndCallback(callback: CompletedCallback): Unit = res.setEndCallback(callback)

  override def close(): Unit = res.close()
}

object HttpResponse {
  def apply(response: AsyncHttpResponse): HttpResponse = new HttpResponseImpl(response)

  import scala.language.implicitConversions

  implicit def wrap(res: AsyncHttpResponse): HttpResponse = apply(res)
  implicit def unwrap(res: HttpResponse): AsyncHttpResponse = res match {
    case wrapper: HttpResponseImpl => wrapper.res
    case _ => throw new IllegalArgumentException(s"Expected Koushikdutta's AsyncHttpResponse, but tried to unwrap: $res")
  }
}

trait ResponseWorker {
  def processResponse(requestUri: Option[URI],
                      response: HttpResponse,
                      decoder: ResponseBodyDecoder,
                      progressCallback: Option[ProgressCallback],
                      networkActivityCallback: () => Unit): CancellableFuture[Response]
}

class ResponseImplWorker extends ResponseWorker {
  protected implicit val dispatcher = new SerialDispatchQueue(Threading.ThreadPool)

  //XXX: has to be executed on Http thread (inside onConnectCompleted), since data callbacks have to be set before this callback completes,
  override def processResponse(requestUri: Option[URI],
                               response: HttpResponse,
                               decoder: ResponseBodyDecoder,
                               progressCallback: Option[ProgressCallback],
                               networkActivityCallback: () => Unit): CancellableFuture[Response] = {
    val httpStatus = response.status
    val contentLength = response.headers("Content-Length").map(_.toInt).getOrElse(-1)
    val contentType = response.headers("Content-Type").getOrElse("")

    debug(s"got connection response, status: '$httpStatus', length: '$contentLength', type: '$contentType', for request $requestUri")

    progressCallback foreach (_(ProgressIndicator.ProgressData(0L, contentLength, api.ProgressIndicator.State.RUNNING)))
    if (contentLength == 0) {
      progressCallback foreach { cb => Future(cb(ProgressIndicator.ProgressData(0, 0, api.ProgressIndicator.State.COMPLETED))) }
      CancellableFuture.successful(Response(httpStatus, headers = response.headers))
    } else {
      val p = Promise[Response]()
      val consumer = decoder(contentType, contentLength)

      def onComplete(ex: Exception) = {
        response.setDataCallback(new NullDataCallback)
        response.setEndCallback(new NullCompletedCallback)
        p.tryComplete(
          if (ex != null) Failure(ex)
          else consumer.result match {
            case Success(body) =>
              progressCallback foreach { cb => Future(cb(ProgressIndicator.ProgressData(contentLength, contentLength, api.ProgressIndicator.State.COMPLETED))) }
              Success(Response(httpStatus, body, response.headers))
            case Failure(t) =>
              progressCallback foreach { cb => Future(cb(ProgressIndicator.ProgressData(0, contentLength, api.ProgressIndicator.State.FAILED))) }
              Success(Response(Response.InternalError(s"Response body consumer failed for request: '$requestUri'", Some(t), Some(httpStatus))))
          }
        )
      }

      response.setDataCallback(new DataCallback {
        val bytesSent = new AtomicLong(0L)

        override def onDataAvailable(emitter: DataEmitter, bb: ByteBufferList): Unit = {
          val numConsumed = bb.remaining
          val state = consumer.consume(bb)

          networkActivityCallback()
          progressCallback foreach { cb => Future(cb(ProgressIndicator.ProgressData(bytesSent.addAndGet(numConsumed), contentLength, api.ProgressIndicator.State.RUNNING))) }

          state match {
            case Done =>
              // consumer doesn't need any more data, we can stop receiving and report success
              debug(s"consumer [$consumer] returned Done, finishing response processing for: $requestUri")
              onComplete(null)
              response.close()
            case _ => // ignore
          }
        }
      })

      response.setEndCallback(new CompletedCallback {
        override def onCompleted(ex: Exception): Unit = {
          debug(s"response for $requestUri ENDED, ex: $ex, p.isCompleted: ${p.isCompleted}")
          Option(ex) foreach { error(s"response for $requestUri failed", _) }
          networkActivityCallback()
          onComplete(ex)
        }
      })

      new CancellableFuture(p) {
        override def cancel()(implicit tag: LogTag): Boolean = {
          debug(s"cancelling response processing for: $requestUri")(tag)
          response.setDataCallback(new NullDataCallback)
          response.setEndCallback(new NullCompletedCallback)
          response.close()
          progressCallback foreach { cb => Future(cb(ProgressIndicator.ProgressData(0, contentLength, api.ProgressIndicator.State.CANCELLED))) }
          super.cancel()(tag)
        }
      }
    }

  }
}


