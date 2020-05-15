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
package com.waz.znet2

import java.io.{ByteArrayInputStream, InputStream}
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.net.{Proxy, UnknownServiceException}

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.CertificatePin
import com.waz.threading.CancellableFuture
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{ExecutorServiceWrapper, IoUtils, RichOption}
import com.waz.znet.ServerTrust
import com.waz.znet2.http.HttpClient.ProgressCallback
import com.waz.znet2.http.Method._
import com.waz.znet2.http.{Headers, _}
import okhttp3.MultipartBody.{Part => OkMultipartBodyPart}
import okhttp3.{CertificatePinner, CipherSuite, ConnectionSpec, Dispatcher, Interceptor, OkHttpClient, TlsVersion, Headers => OkHeaders, MediaType => OkMediaType, MultipartBody => OkMultipartBody, Request => OkRequest, RequestBody => OkRequestBody, Response => OkResponse}
import okio.BufferedSink

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * According to OkHttp response body parsing logic, we get an OkHttp Body object with empty content in case,
  * when we receive http response without body.
  * We would like to do not have response body in this case.
  * For achieving this, we always check response Content-Type header, and, if it is not present,
  * we ignore the parsed OkHttp response body. This is done in HttpClientOkHttpImpl.convertOkHttpResponse
  */
class HttpClientOkHttpImpl(client: OkHttpClient)(implicit protected val ec: ExecutionContext)
  extends HttpClient with DerivedLogTag {

  import HttpClient._
  import HttpClientOkHttpImpl._

  protected def executeIgnoreInterceptor(
      request: Request[Body],
      uploadCallback: Option[ProgressCallback] = None,
      downloadCallback: Option[ProgressCallback] = None
  ): CancellableFuture[Response[Body]] =
    CancellableFuture { client.newCall(convertHttpRequest(request, uploadCallback)) }
      .flatMap { okCall =>
        CancellableFuture.lift(
          future = Future { okCall.execute() }
            .recoverWith {
              case ex: UnknownServiceException =>
                error(l"failure while getting okHttp response, unknown service.", ex)
                Future.failed(UnknownServiceError(ex))
              case err =>
                error(l"failure while getting okHttp response.", err)
                Future.failed(ConnectionError(err))
            }
            .map(convertOkHttpResponse(_, downloadCallback)),
          onCancel = {
            verbose(l"cancel executing okHttp request: $request")
            okCall.cancel()
          }
        )
      }
}

object HttpClientOkHttpImpl {

  def apply(enableLogging: Boolean, timeout: Option[FiniteDuration] = None,
            pin: CertificatePin = ServerTrust.wirePin,
            proxy: Option[Proxy] = None,
            customUserAgentInterceptor: Option[Interceptor] = None)
           (implicit ec: ExecutionContext): HttpClientOkHttpImpl =
    new HttpClientOkHttpImpl(
      createOkHttpClient(
        Some(createModernConnectionSpec),
        Some(createCertificatePinner(pin)),
        if (enableLogging) Some(createLoggerInterceptor) else None,
        proxy,
        customUserAgentInterceptor,
        timeout
      )
    )

  def createOkHttpClient(
      connectionSpec: Option[ConnectionSpec] = None,
      certificatePinner: Option[CertificatePinner] = None,
      loggerInterceptor: Option[Interceptor] = None,
      proxy: Option[Proxy] = None,
      customUserAgentInterceptor: Option[Interceptor] = None,
      timeout: Option[FiniteDuration] = None
  )(implicit ec: ExecutionContext): OkHttpClient = {
    val builder = new OkHttpClient.Builder()
    connectionSpec.foreach(spec => builder.connectionSpecs(List(spec, ConnectionSpec.CLEARTEXT).asJava))
    certificatePinner.foreach(builder.certificatePinner)
    customUserAgentInterceptor.foreach(builder.addInterceptor)
    proxy.foreach(p => builder.proxy(p))
    loggerInterceptor.foreach(builder.addInterceptor)
    timeout.foreach { t =>
      builder.connectTimeout(t.toMillis, TimeUnit.MILLISECONDS)
      builder.writeTimeout(t.toMillis, TimeUnit.MILLISECONDS)
      builder.readTimeout(t.toMillis, TimeUnit.MILLISECONDS)
    }

    builder
      .dispatcher(createDispatcher(ec))
      .build()
  }

  def createCertificatePinner(pin: CertificatePin): CertificatePinner = {
    val publicKeySha256 = MessageDigest.getInstance("SHA-256").digest(pin.cert)
    new CertificatePinner.Builder()
      .add(pin.domain, "sha256/" + AESUtils.base64(publicKeySha256))
      .build()
  }

  def createModernConnectionSpec: ConnectionSpec =
    new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      )
      .build()

  def createLoggerInterceptor: Interceptor =
    new OkHttpLoggingInterceptor(List(MediaType.Json, MediaType.PlainText))

  def createDispatcher(ec: ExecutionContext): Dispatcher =
    new Dispatcher(new ExecutorServiceWrapper()(ec))

  def convertHttpMethod(method: http.Method): String =
    method match {
      case Get    => "GET"
      case Post   => "POST"
      case Put    => "PUT"
      case Patch  => "PATCH"
      case Delete => "DELETE"
      case Head   => "HEAD"
    }

  def convertMediaType(mediatype: String): OkMediaType =
    OkMediaType.parse(mediatype)

  def convertHeaders(headers: OkHeaders): Headers =
    Headers(headers.toMultimap.asScala.mapValues(_.asScala.head).toMap)

  def convertHeaders(headers: Headers): OkHeaders =
    OkHeaders.of(headers.headers.asJava)

  private def createRequestBody(body: http.Body,
                                callback: Option[ProgressCallback],
                                headers: Headers): Option[OkRequestBody] =
    body match {
      case _: http.EmptyBody  => None
      case body: http.RawBody => Some(createOkHttpRequestBody(callback, body, headers))
      case body: http.RawMultipartBodyFormData =>
        Some(createOkHttpMultipartRequestBodyFormData(callback, body))
      case body: http.RawMultipartBodyMixed =>
        Some(createOkHttpMultipartRequestBodyMixed(callback, body))
    }

  def convertHttpRequest(request: http.Request[http.Body], callback: Option[ProgressCallback] = None): OkRequest =
    new OkRequest.Builder()
      .url(request.url)
      .method(convertHttpMethod(request.httpMethod), createRequestBody(request.body, callback, request.headers).orNull)
      .headers(convertHeaders(request.headers))
      .build()

  def convertOkHttpResponse(response: OkResponse, callback: Option[ProgressCallback]): http.Response[http.Body] =
    http.Response(
      code = response.code(),
      headers = convertHeaders(response.headers()),
      body = Option(response.body())
        .filterNot(_ => response.header("Content-Type") == null || response.code() == ResponseCode.NoResponse) // should be treated as empty body
        .map { body =>
          val data       = body.byteStream()
          val dataLength = if (body.contentLength() == -1) None else Some(body.contentLength())
          http.RawBody(
            mediaType = Option(body.contentType()).map(_.toString),
            data = () => callback.map(createProgressInputStream(_, data, dataLength)).getOrElse(data),
            dataLength = dataLength
          )
        }
        .getOrElse(EmptyBodyImpl)
    )

  private def applyContentEncoding(body: RawBody, headers: Headers): RawBody =
    headers.get("Content-Encoding") match {
      case None => body
      case Some(encType) if encType.equalsIgnoreCase("gzip") =>
        val zipped = IoUtils.gzip(IoUtils.toByteArray(body.data()))
        body.copy(data = () => new ByteArrayInputStream(zipped), dataLength = Some(zipped.length))
      case _ =>
        throw new IllegalArgumentException("Unsupported content encoding.")
    }

  def createOkHttpRequestBody(callback: Option[ProgressCallback], body: http.RawBody, headers: Headers): OkRequestBody =
    new OkRequestBody() {
      private lazy val encodedBody          = applyContentEncoding(body, headers)
      override val contentType: OkMediaType = encodedBody.mediaType.map(convertMediaType).orNull
      override val contentLength: Long      = encodedBody.dataLength.getOrElse(-1L)

      def writeTo(sink: BufferedSink): Unit = IoUtils.copy(
        in = callback
          .map(createProgressInputStream(_, encodedBody.data(), encodedBody.dataLength))
          .getOrElse(encodedBody.data()),
        out = sink.outputStream()
      )
    }

  private def createProgressInputStream(callback: ProgressCallback,
                                        data: InputStream,
                                        dataLength: Option[Long]): ProgressInputStream =
    new ProgressInputStream(
      data,
      new ProgressInputStream.Listener {
        override def progressUpdated(bytesRead: Long, totalBytesRead: Long): Unit =
          callback.updated(totalBytesRead, dataLength)
      }
    )

  def createOkHttpMultipartRequestBodyMixed(callback: Option[ProgressCallback],
                                            body: RawMultipartBodyMixed): OkRequestBody =
    createOkHttpMultipartRequestBody(
      callback,
      http.MediaType.MultipartMixed,
      body.parts.map(p => p.copy(body = applyContentEncoding(p.body, p.headers)))
    )(
      _.body,
      part => OkMultipartBodyPart.create(convertHeaders(part.headers), _)
    )

  def createOkHttpMultipartRequestBodyFormData(callback: Option[ProgressCallback],
                                               body: RawMultipartBodyFormData): OkRequestBody =
    createOkHttpMultipartRequestBody(
      callback,
      http.MediaType.MultipartFormData,
      body.parts
    )(
      _.body,
      part => OkMultipartBodyPart.createFormData(part.name, part.fileName.orNull, _)
    )

  def createOkHttpMultipartRequestBody[Part](callback: Option[ProgressCallback], mediaType: String, parts: Seq[Part])(
      getRawBody: Part => RawBody,
      getOkPartCreator: Part => OkRequestBody => OkMultipartBodyPart
  ): OkRequestBody = {

    val totalProgressListener = callback.map { c =>
      new ProgressInputStream.Listener {
        var total: Option[Long] = RichOption.traverse(parts)(getRawBody(_).dataLength).map(_.sum)
        var lastProgress: Long = 0
        override def progressUpdated(bytesRead: Long, totalBytesRead: Long): Unit = {
          lastProgress = lastProgress + bytesRead
          c.updated(lastProgress, total)
        }
      }
    }

    val partCreator: Seq[OkMultipartBody.Part] = parts
      .map { p =>
        val okHttpBody = new OkRequestBody {
          private val body                      = getRawBody(p)
          override val contentType: OkMediaType = body.mediaType.map(convertMediaType).orNull
          override val contentLength: Long      = body.dataLength.getOrElse(-1)

          def writeTo(sink: BufferedSink): Unit = {
            val dataInputStream = totalProgressListener.fold(body.data())(new ProgressInputStream(body.data(), _))
            IoUtils.withResource(dataInputStream) { in =>
              IoUtils.write(in, sink.outputStream())
            }
          }
        }

        getOkPartCreator(p)(okHttpBody)
      }

    val builder = new OkMultipartBody.Builder()
    partCreator.foreach { case p: OkMultipartBodyPart => builder.addPart(p) }

    builder.setType(convertMediaType(mediaType)).build()
  }

}
