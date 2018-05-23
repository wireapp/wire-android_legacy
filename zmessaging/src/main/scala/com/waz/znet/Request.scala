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

import java.io.{File, InputStream}
import java.net.URLEncoder

import com.google.protobuf.nano.MessageNano
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.http.AsyncHttpRequest
import com.koushikdutta.async.http.body._
import com.koushikdutta.async.{DataEmitter, DataSink, Util}
import com.waz.api.impl.ProgressIndicator
import com.waz.utils
import com.waz.utils.wrappers.URI
import com.waz.utils.{ExponentialBackoff, IoUtils, JsonEncoder}
import com.waz.znet.ContentEncoder.{EmptyContentEncoder, EmptyRequestContent, RequestContent}
import com.waz.znet.Request.ProgressCallback
import com.waz.znet.Response.{ResponseBodyDecoder, Status}
import org.json.JSONObject

import scala.concurrent.duration._

case class Request[A: ContentEncoder](httpMethod:             String                      = Request.GetMethod,
                                      resourcePath:           Option[String]              = None,
                                      baseUri:                Option[URI]                 = None,
                                      data:                   Option[A]                   = None,
                                      decoder:                Option[ResponseBodyDecoder] = None,
                                      uploadCallback:         Option[ProgressCallback]    = None,
                                      downloadCallback:       Option[ProgressCallback]    = None,
                                      requiresAuthentication: Boolean                     = true,
                                      headers:                Map[String, String]         = Request.EmptyHeaders,
                                      retryPolicy:            RetryPolicy                 = RetryPolicy.NeverRetry,
                                      followRedirect:         Boolean                     = true,
                                      timeout:                FiniteDuration              = AsyncClient.DefaultTimeout
) extends HttpRequest {

  assert(uploadCallback.isEmpty, "uploadCallback is not supported yet") //TODO

  require(resourcePath.isDefined || baseUri.isDefined, "Either resourcePath or baseUri has to be specified")

  def getBody = data.map(implicitly[ContentEncoder[A]].apply).getOrElse(EmptyRequestContent)

  def withHeaders(headers: Map[String, String]) = copy[A](headers = this.headers ++ headers)
  def withTimeout(timeout: FiniteDuration) = copy[A](timeout = timeout)
  def withBaseUri(uri: URI) = copy[A](baseUri = Option(uri))
  def withBaseUriIfNone(uri: URI) = baseUri.map(_ => this).getOrElse(withBaseUri(uri))

  override lazy val absoluteUri: Option[URI] = baseUri match {
    case Some(uri) => Some(resourcePath.map(path => uri.buildUpon.appendEncodedPath(path).build).getOrElse(uri))
    case None => None
  }
}

object Request {
  type ProgressCallback = ProgressIndicator.ProgressData => Unit

  val PostMethod = "POST"
  val PutMethod = "PUT"
  val GetMethod = "GET"
  val DeleteMethod = "DELETE"
  val HeadMethod = "HEAD"

  val EmptyHeaders = Map[String, String]()

  def Post[A: ContentEncoder](path: String,
                              data: A,
                              baseUri: Option[URI] = None,
                              uploadCallback: Option[ProgressCallback] = None,
                              requiresAuthentication: Boolean = true,
                              headers: Map[String, String] = EmptyHeaders,
                              timeout: FiniteDuration = AsyncClient.DefaultTimeout) =
    Request[A](PostMethod, resourcePath = Some(path), baseUri = baseUri, data = Some(data),
               uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication,
               headers = headers, timeout = timeout)

  def Put[A: ContentEncoder](path: String,
                             data: A,
                             baseUri: Option[URI] = None,
                             uploadCallback: Option[ProgressCallback] = None,
                             requiresAuthentication: Boolean = true,
                             headers: Map[String, String] = EmptyHeaders,
                             timeout: FiniteDuration = AsyncClient.DefaultTimeout) =
    Request[A](PutMethod, resourcePath = Some(path), baseUri = baseUri, data = Some(data),
               uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication,
               headers = headers, timeout = timeout)

  def Delete[A: ContentEncoder](path: String,
                                data: Option[A] = None,
                                baseUri: Option[URI] = None,
                                requiresAuthentication: Boolean = true,
                                headers: Map[String, String] = EmptyHeaders,
                                timeout: FiniteDuration = AsyncClient.DefaultTimeout) =
    Request[A](DeleteMethod, resourcePath = Some(path), baseUri = baseUri, data = data, requiresAuthentication = requiresAuthentication, headers = headers, timeout = timeout)

  def Get(path:                   String,
          baseUri:                Option[URI]                 = None,
          downloadCallback:       Option[ProgressCallback]    = None,
          requiresAuthentication: Boolean                     = true,
          headers:                Map[String, String]         = EmptyHeaders,
          timeout:                FiniteDuration              = AsyncClient.DefaultTimeout,
          decoder:                Option[ResponseBodyDecoder] = None) =
    Request[Unit](GetMethod, resourcePath = Some(path), baseUri = baseUri, downloadCallback = downloadCallback, requiresAuthentication = requiresAuthentication, headers = headers, timeout = timeout, decoder = decoder)(EmptyContentEncoder)

  def Head(path: String,
           baseUri: Option[URI] = None,
           downloadCallback: Option[ProgressCallback] = None,
           requiresAuthentication: Boolean = true,
           headers: Map[String, String] = EmptyHeaders) =
    Request[Unit](HeadMethod, resourcePath = Some(path), baseUri = baseUri, downloadCallback = downloadCallback,
                  requiresAuthentication = requiresAuthentication, headers = headers)(EmptyContentEncoder)

  def query(path: String, args: (String, Any)*): String = {
    args map {
      case (key, value) =>
        URLEncoder.encode(key, "utf8") + "=" + URLEncoder.encode(value.toString, "utf8")
    } mkString (path + (if (path.contains('?')) "&" else "?"), "&", "")
  }
}

trait ContentEncoder[A] { self =>
  def apply(data: A): RequestContent

  def map[B](f: B => A): ContentEncoder[B] = new ContentEncoder[B] {
    override def apply(data: B): RequestContent = self(f(data))
  }
}

object ContentEncoder {
  val MinGzipBytesLength = 250  // minimum content size when gzip should be enabled, no point compressing smaller content

  sealed trait RequestContent {
    protected def asyncHttpBody: Option[AsyncHttpRequestBody[_]] = None

    def apply(req: AsyncHttpRequest): AsyncHttpRequest = {
      asyncHttpBody foreach req.setBody
      req
    }
  }

  case object EmptyRequestContent extends RequestContent

  trait ByteArrayRequestContent extends RequestContent {
    def data: Array[Byte]
    def contentType: String

    def sourceData: Array[Byte] = data

    override def asyncHttpBody = Some(new AsyncHttpRequestBody[Unit] {
      override def get(): Unit = ()
      override def length(): Int = data.length
      override def readFullyOnRequest(): Boolean = false
      override def getContentType: String = contentType
      override def parse(emitter: DataEmitter, completed: CompletedCallback): Unit = { throw new UnsupportedOperationException("Temp request body should only be used for writing") }
      override def write(request: AsyncHttpRequest, sink: DataSink, completed: CompletedCallback): Unit = Util.writeAll(sink, data, completed)
    })
  }

  case class BinaryRequestContent(data: Array[Byte], contentType: String) extends ByteArrayRequestContent {
    override def toString: String = {
      val ct = Option(contentType)
      if (ct.exists(_.contains("text")) || ct.exists(_.contains("json")))
        s"BinaryRequestContent(${new String(data, "utf8")}, $ct)"
      else
        s"BinaryRequestContent(data len:${data.length}, $ct)"
    }

    def gzipped = GzippedRequestContent(data, contentType)
  }

  case class GzippedRequestContent(bytes: Array[Byte], contentType: String) extends ByteArrayRequestContent {

    override def sourceData: Array[Byte] = bytes

    override lazy val data = {
      if (bytes.length <= MinGzipBytesLength) bytes
      else {
        val zip = IoUtils.gzip(bytes)
        if (zip.length >= bytes.length) bytes
        else zip
      }
    }

    override def apply(req: AsyncHttpRequest): AsyncHttpRequest = {
      if (data ne bytes) req.setHeader("Content-Encoding", "gzip")
      super.apply(req)
    }

    override def toString: String = {
      val ct = Option(contentType)
      if (ct.exists(_.contains("text")) || ct.exists(_.contains("json")))
        s"GzippedRequestContent(orig size: ${bytes.length}, gzip size:${data.length}, ${new String(bytes, "utf8")}, $ct)"
      else
        s"GzippedRequestContent(orig size: ${bytes.length}, gzip size:${data.length}, $ct)"
    }
  }

  case class StreamRequestContent(stream: InputStream, contentType: String, length: Int) extends RequestContent { self =>
    override def asyncHttpBody = Some(new AsyncHttpRequestBody[Unit] {
      override def get(): Unit = ()
      override def length(): Int = self.length
      override def readFullyOnRequest(): Boolean = false
      override def getContentType: String = self.contentType
      override def parse(emitter: DataEmitter, completed: CompletedCallback): Unit = { throw new UnsupportedOperationException("Temp request body should only be used for writing") }
      override def write(request: AsyncHttpRequest, sink: DataSink, completed: CompletedCallback): Unit = Util.pump(stream, if (self.length < 0) Integer.MAX_VALUE else self.length, sink, completed)
    })
  }

  class MultipartRequestContent(parts: Seq[Part], contentType: String = MultipartFormDataBody.CONTENT_TYPE) extends RequestContent {
    override def asyncHttpBody = Some(utils.returning(new MultipartFormDataBody()) { mp =>
      mp.setContentType(contentType)
      parts.foreach(mp.addPart)
    })

    override def toString: String = s"MultipartRequestContent($parts, $contentType)"
  }

  object MultipartRequestContent {
    def apply(files: Seq[(String, File)]): MultipartRequestContent = new MultipartRequestContent(files.map { case (name, file) => new FilePart(name, file) })
  }

  implicit object RequestContentEncoder extends ContentEncoder[RequestContent] {
    override def apply(data: RequestContent) = data
  }

  implicit object BinaryContentEncoder extends ContentEncoder[BinaryRequestContent] {
    override def apply(data: BinaryRequestContent) = data
  }

  implicit object MultipartContentEncoder extends ContentEncoder[MultipartRequestContent] {
    override def apply(data: MultipartRequestContent) = data
  }

  implicit object GzippedContentEncoder extends ContentEncoder[GzippedRequestContent] {
    override def apply(data: GzippedRequestContent) = data
  }

  implicit object StreamedContentEncoder extends ContentEncoder[StreamRequestContent] {
    override def apply(data: StreamRequestContent) = data
  }

  implicit object EmptyContentEncoder extends ContentEncoder[Unit] {
    override def apply(data: Unit) = EmptyRequestContent
  }

  implicit object StringContentEncoder extends ContentEncoder[String] {
    override def apply(data: String) = BinaryRequestContent(data.getBytes("utf8"), "text/plain")
  }

  implicit object JsonContentEncoder extends ContentEncoder[JSONObject] {
    override def apply(data: JSONObject) = GzippedRequestContent(data.toString.getBytes("utf8"), "application/json")
  }

  implicit def json[A: JsonEncoder]: ContentEncoder[A] = JsonContentEncoder.map(implicitly[JsonEncoder[A]].apply)

  def gzipJson[A: JsonEncoder]: ContentEncoder[A] = new ContentEncoder[A] {
    override def apply(data: A): RequestContent = GzippedRequestContent(implicitly[JsonEncoder[A]].apply(data).toString.getBytes("utf8"), "application/json")
  }

  def protobuf(msg: MessageNano) = BinaryRequestContent(MessageNano.toByteArray(msg), "application/x-protobuf")
}

//TODO Remove this
trait RetryPolicy {
  val backoff: ExponentialBackoff
  def shouldRetry(status: Response.Status, retry: Int): Boolean
}

//TODO Remove this
object RetryPolicy {
  object NeverRetry extends RetryPolicy {
    override val backoff: ExponentialBackoff = new ExponentialBackoff(1.second, 1.second)
    override def shouldRetry(status: Status, retry: Int): Boolean = false
  }

  def apply(maxRetries: Int, initialDelay: FiniteDuration = 250.millis, maxDelay: FiniteDuration = 15.seconds) = new RetryPolicy {
    override val backoff = new ExponentialBackoff(initialDelay, maxDelay)

    override def shouldRetry(status: Status, retry: Int): Boolean = {
      retry < maxRetries
    }
  }
}
