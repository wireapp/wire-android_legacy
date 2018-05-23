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


import java.io.{File, PipedInputStream, PipedOutputStream}

import com.koushikdutta.async.callback.CompletedCallback.NullCompletedCallback
import com.koushikdutta.async.callback.DataCallback.NullDataCallback
import com.koushikdutta.async.callback._
import com.koushikdutta.async.http.{WebSocket => AWebSocket}
import com.koushikdutta.async.http._
import com.koushikdutta.async.http.callback._
import com.koushikdutta.async._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.ProgressIndicator.State
import com.waz.api.impl.ProgressIndicator
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.{IoUtils, Json}
import com.waz.utils.wrappers.URI
import com.waz.znet.ContentEncoder.{GzippedRequestContent, JsonContentEncoder, MultipartRequestContent, StreamRequestContent}
import com.waz.znet.Response.{DefaultResponseBodyDecoder, HttpStatus, SuccessHttpStatus}
import org.json.JSONObject
import com.waz.testutils.Slow
import com.waz.threading.CancellableFuture.CancelException

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.util.Random

class AsyncClientSpec extends AndroidFreeSpec {
  implicit lazy val dispatcher = new SerialDispatchQueue

  class FakeHttpResponse(c: Int = 200, m: String = "meep", h: Headers = new Headers(), body: Option[Array[Byte]] = None, completeOnSet: Boolean = true) extends AsyncHttpResponse {
    @volatile var dataCallback: DataCallback = new NullDataCallback
    @volatile var endCallback: CompletedCallback = new NullCompletedCallback

    @volatile var alreadyConsumed = false

    private def consume() = synchronized {
      dataCallback match {
        case c: NullDataCallback => // do nothing
        case c: DataCallback if m.nonEmpty && !alreadyConsumed =>
          val bb = new ByteBufferList(body.getOrElse(m.toCharArray.map(_.toByte)))
          c.onDataAvailable(null, bb)
          alreadyConsumed = true
        case _ =>
      }
    }

    private def complete() = synchronized {
      endCallback match {
        case c: NullCompletedCallback => // do nothing
        case c: CompletedCallback => consume(); c.onCompleted(null)
        case _ =>
      }
    }

    override def setEndCallback(callback: CompletedCallback): Unit = {
      endCallback = callback
      if(completeOnSet) Future { complete() }
    }

    def getEndCallback: CompletedCallback = endCallback

    override def setDataCallback(callback: DataCallback): Unit = {
      dataCallback = callback
      if(completeOnSet) Future { consume() }
    }

    def getDataCallback: DataCallback = dataCallback

    override def getRequest: AsyncHttpRequest = null
    override def message(): String = m
    override def code(): Int = c
    override def protocol(): String = "http"
    override def headers(): Headers = h
    override def detachSocket(): AsyncSocket = null
    override def isPaused: Boolean = false
    override def getServer: AsyncServer = null
    override def isChunked: Boolean = false
    override def charset(): String = "UTF-8"
    override def pause(): Unit = ()
    override def close(): Unit = ()
    override def resume(): Unit = ()
  }

  private val uriRegex = """.*(get|post)/(json|multi)*[_]*(empty|json)(\d+)""".r

  private val jsonRequest = """{"key": "value"}"""
  private val okJsonResponse = """{"result": "ok"}"""
  private val penguinData = "some penguin data"
  private val jsonObject = Json((1 to 25).map(i => s"key_$i" -> Seq.tabulate(i)(identity).mkString(",")).toMap)

  private def toHeaders(tuples: (String, String)*): Headers = {
    val headers = new Headers()
    tuples.foreach(t => headers.set(t._1, t._2))
    headers
  }

  private def contentLength(data: String): (String, String) = "Content-Length" -> data.length.toString
  private def contentLength(body: Array[Byte]): (String, String) = "Content-Length" -> body.length.toString

  private def mockResponse(req: HttpRequest): HttpResponse =
    req.absoluteUri.getOrElse(throw new IllegalArgumentException("No URI found")).getPath match {
      case uriRegex(method, json, expect, code) if expect == "empty" => new FakeHttpResponse(code.toInt, "", toHeaders(contentLength("")))

      case uriRegex(method, json, expect, code) if expect == "json" =>
        new FakeHttpResponse(code.toInt, okJsonResponse, toHeaders("Content-Type" -> "application/json", contentLength(okJsonResponse)))

      case str: String if str.endsWith("/get/penguin") =>
        new FakeHttpResponse(200, penguinData, toHeaders("Content-Type" -> "image/png", contentLength(penguinData)))

      case str: String if str.endsWith("/get/gzipped") =>
        val gzipped = IoUtils.gzip(jsonObject.toString.getBytes("utf8"))
        new FakeHttpResponse(200, jsonObject.toString, toHeaders("Content-Type" -> "application/json", "Content-Encoding" -> "gzip", contentLength(gzipped)))

      case str: String if str.endsWith("/post/gzipped") =>
        val gzipped = IoUtils.gzip(okJsonResponse.getBytes("utf8"))
        new FakeHttpResponse(200, okJsonResponse, toHeaders("Content-Type" -> "application/json", "Content-Encoding" -> "gzip", contentLength(gzipped)))

      case str: String if str.endsWith("/get/delayed") => new FakeHttpResponse(200, okJsonResponse, toHeaders("Content-Type" -> "application/json", contentLength(okJsonResponse)))

      case other => throw new IllegalArgumentException(s"Unrecognized URI: $other")
    }

  private val requestWorker = new RequestWorker {
    override def processRequest(req: HttpRequest): HttpRequest = req
  }

  class FakeClientWrapper(delay: Option[Long] = None) extends ClientWrapper {
    override def execute(request: HttpRequest, callback: HttpConnectCallback): CancellableFuture[HttpResponse] = {
      val p = Promise[HttpResponse]
      Future {
        delay.map(Thread.sleep)
        val fakeResponse = mockResponse(request)
        callback.onConnectCompleted(null, fakeResponse)
        p.success(fakeResponse)
      }
      CancellableFuture.lift(p.future)
    }

    override def websocket(request: HttpRequest, protocol: String, callback: AsyncHttpClient.WebSocketConnectCallback): CancellableFuture[AWebSocket] = ???
    override def stop(): Unit = ???
  }

  private def client = new AsyncClientImpl(
    bodyDecoder = DefaultResponseBodyDecoder,
    userAgent="test",
    wrapper = Future { new FakeClientWrapper(Some(100L)) },
    requestWorker = requestWorker,
    responseWorker = new ResponseImplWorker
  )

  private def clientWithDelay = new AsyncClientImpl(
    bodyDecoder = DefaultResponseBodyDecoder,
    userAgent="test",
    wrapper = Future { new FakeClientWrapper(Some(3000L)) },
    requestWorker = requestWorker,
    responseWorker = new ResponseImplWorker
  )

  @volatile private var progress = ProgressIndicator.ProgressData(0L, 0L, State.UNKNOWN)
  @volatile private var progressTickCount: Int = 0

  private val callback = (currentProgress: ProgressIndicator.ProgressData) => {
    progressTickCount += 1
    progress = currentProgress
  }

  private def doGet(path: String, auth: Boolean = false, waitTime: FiniteDuration = 500.millis) = {
    val request = Request.Get(path, baseUri = Some(URI.parse("http://localhost")), downloadCallback = Some(callback), requiresAuthentication = auth)
    Await.result(client(request), waitTime)
  }

  private def doPost[A: ContentEncoder](path: String, data: A, auth: Boolean = false, timeout: FiniteDuration = AsyncClient.DefaultTimeout, waitTime: FiniteDuration = 500.millis, client: AsyncClientImpl = client) = {
    val request = Request.Post(path, implicitly[ContentEncoder[A]].apply(data), baseUri = Some(URI.parse("http://localhost")), requiresAuthentication = auth, timeout = timeout)
    Await.result(client(request), waitTime)
  }

  feature("GET request") {

    scenario("Empty http GET request - 200") {
      doGet("/get/empty200") match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Empty http GET request - 400") {
      doGet("/get/empty400") match {
        case Response(HttpStatus(400, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(400))")
      }
    }

    scenario("Perform json GET request") {

      doGet("/get/json200") match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), headers) if js.toString == new JSONObject(okJsonResponse).toString && headers("Content-Type").contains("application/json") => info(s"got json response with headers: $headers") //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($okJsonResponse))")
      }
    }

    scenario("Perform image GET request") {
      progressTickCount = 0
      doGet("/get/penguin") match {
        case Response(HttpStatus(200, _), _, _) => info(s"got 200") // expected
        case r => fail(s"got: $r instead of 200")
      }
      progressTickCount should be > 0
      progress.current should be > 0L
      progress.current shouldEqual progress.total
      progress.state shouldEqual State.COMPLETED
    }

    // I'm not sure if this test actually does what it says it does...
    scenario("Get gzipped json") {
      import scala.collection.JavaConverters._

      def toMap(js: JSONObject) =
        js.keys().asInstanceOf[java.util.Iterator[String]].asScala.map(key => key -> js.getString(key)).toMap

      val obj = Json((1 to 25).map(i => s"key_$i" -> Seq.tabulate(i)(identity).mkString(",")).toMap)

      val gzipped = IoUtils.gzip(obj.toString.getBytes("utf8"))

      doGet("/get/gzipped") match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), headers) => toMap(js) shouldEqual toMap(obj)
        case r => fail(s"got unexpected response: $r")
      }
    }
  }

  feature("POST request") {

    scenario("Empty http POST request - 200") {
      doPost("/post/empty200", {}) match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Post json, receive empty 200") {
      doPost("/post/json_empty200", new JSONObject(jsonRequest)) match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

   scenario("Post json, receive json 200") {
      doPost("/post/json_json200", new JSONObject(jsonRequest)) match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), _) if js.toString == new JSONObject(okJsonResponse).toString => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($okJsonResponse))")
      }
    }
  }

  feature("Cancelling") {

    val baseUri = Some(URI.parse("http://localhost"))

    scenario("Cancel once completed") {
      val path = "/get/empty200"
      val future = client(Request.Get(path, baseUri = baseUri))
      Thread.sleep(200L)
      future.cancel()

      Await.result(future, 200.millis) match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => //expected
        case resp => fail(s"got: $resp while expecting Response(HttpStatus(200))")
      }
    }

    scenario("Cancel right away", Slow) {
      (0 to 100) map { _ =>
        val path = "/get/json200"
        val future = client(Request.Get(path, baseUri = baseUri))
        future.cancel()
        future
      } foreach { future =>
        intercept[CancelException] {
          Await.result(future, 100.millis)
        }
      }
    }

    scenario("Cancel randomly", Slow) {
      (0 to 255) foreach { _ =>
        val path = "/get/json200"
        val future = client(Request.Get(path, baseUri = baseUri))
        Thread.sleep(Random.nextInt(50))
        future.cancel()
        try {
          Await.result(future, 100.millis) match {
            case Response(HttpStatus(200, _), JsonObjectResponse(js), _) => //expected
            case resp => fail(s"got unexpected response: $resp")
          }
        } catch {
          case _: CancelException => // fine
        }
      }
    }

    scenario("Cancel randomly on post", Slow) {
      val data = JsonContentEncoder(new JSONObject(jsonRequest))
      (0 to 255) foreach { _ =>
        val path = "/post/json_json200"
        val future = client(Request.Post(path, data, baseUri = baseUri, timeout = AsyncClient.DefaultTimeout))
        Thread.sleep(Random.nextInt(50))
        future.cancel()
        try {
          Await.result(future, 100.millis) match {
            case Response(HttpStatus(200, _), JsonObjectResponse(js), _) => //expected
            case resp => fail(s"got unexpected response: $resp")
          }
        } catch {
          case _: CancelException => // fine
        }
      }
    }

    scenario("Cancel long get") {
      val path = "/get/delayed"
      val future = clientWithDelay(Request.Get(path, baseUri = baseUri))
      Thread.sleep(100)

      future.cancel()

      intercept[CancelException] {
        Await.result(future, 100.millis)
      }
    }

    scenario("Cancel while streaming POST") {
      val os = new PipedOutputStream()
      val is = new PipedInputStream(os)
      val path = "/post/json_json200"
      val future = client(Request.Post(path, StreamRequestContent(is, "application/json", -1), baseUri = baseUri, timeout = AsyncClient.DefaultTimeout))
      os.write("""{"key":"""".getBytes("utf8"))
      future.cancel()
      os.write("""value"}""".getBytes("utf8"))

      intercept[CancelException] {
        Await.result(future, 100.millis)
      }
    }
  }

  feature("Gzip POST encoding") {

    scenario("Send gzipped json request") {
      val obj = Json((1 to 100).map(i => s"key_$i" -> Seq.tabulate(i)(identity).mkString(",")).toMap)

      val path = "/post/gzipped"
      val future = client(Request.Post(path, GzippedRequestContent(obj.toString.getBytes("utf8"), "application/json"), baseUri = Some(URI.parse("http://localhost")), timeout = AsyncClient.DefaultTimeout))
      Await.result(future, AsyncClient.DefaultTimeout) match {
        case Response(SuccessHttpStatus(), JsonObjectResponse(json), headers) if json.getString("result") == "ok" && headers("Content-Encoding") == Some("gzip") => // fine
        case other => fail(s"Wrong response: $other")
      }

    }
  }

  feature("Network inactivity timeout") {

    val baseUri = Some(URI.parse("http://meep.me"))
    lazy val mockedResponse = new FakeHttpResponse(200, "", completeOnSet = false)

    @volatile var onConnect: Option[HttpConnectCallback] = None

    lazy val clientWrapper = new ClientWrapper {
      override def execute(req: HttpRequest, callback: HttpConnectCallback): CancellableFuture[HttpResponse] = {
        onConnect = Option(callback)
        CancellableFuture(mockedResponse)
      }

      def websocket(request: HttpRequest, protocol: String, callback: AsyncHttpClient.WebSocketConnectCallback): CancellableFuture[AWebSocket] = ???
      def stop(): Unit = {}
    }

    lazy val mockedClient = new AsyncClientImpl(
      bodyDecoder = DefaultResponseBodyDecoder,
      userAgent="test",
      wrapper = Future { clientWrapper },
      requestWorker = requestWorker,
      responseWorker = new ResponseImplWorker
    )

    scenario("connect doesn't complete") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      a[TimeoutException] should be thrownBy Await.result(resp, 2.seconds)
    }

    scenario("connect completes, no response is sent") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      Thread.sleep(100L)
      onConnect.foreach { _.onConnectCompleted(null, mockedResponse) }
      a[TimeoutException] should be thrownBy Await.result(resp, 2.seconds)
    }

    scenario("connect completes, response is sent, but no completion") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      Thread.sleep(100L)
      onConnect.foreach { _.onConnectCompleted(null, mockedResponse) }
      Thread.sleep(100L)
      mockedResponse.dataCallback.onDataAvailable(null, new ByteBufferList)
      a[TimeoutException] should be thrownBy Await.result(resp, 2.seconds)
    }

    scenario("connect completes, response is sent, in time completion") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      Thread.sleep(100L)
      onConnect.foreach { _.onConnectCompleted(null, mockedResponse) }
      Thread.sleep(100L)
      mockedResponse.dataCallback.onDataAvailable(null, new ByteBufferList)
      Thread.sleep(100L)
      mockedResponse.endCallback.onCompleted(null)
      Await.result(resp, 2.seconds).status.status shouldEqual 200
    }

    scenario("connect completes, response is sent slowly, in time completion") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      Thread.sleep(100L)
      onConnect.foreach { _.onConnectCompleted(null, mockedResponse) }
      for (i <- 1 to 3) {
        Thread.sleep(500L)
        mockedResponse.dataCallback.onDataAvailable(null, new ByteBufferList)
      }
      Thread.sleep(100L)
      mockedResponse.endCallback.onCompleted(null)
      Await.result(resp, 2.seconds).status.status shouldEqual 200
    }

    scenario("connect completes, response is sent slowly, then takes too long") {
      val resp = mockedClient(Request.Get("", baseUri = baseUri, timeout = 1.second))
      Thread.sleep(100L)
      onConnect.foreach { _.onConnectCompleted(null, mockedResponse) }
      for (i <- 1 to 2) {
        Thread.sleep(500L)
        mockedResponse.dataCallback.onDataAvailable(null, new ByteBufferList)
      }
      Thread.sleep(1100L)
      mockedResponse.dataCallback.onDataAvailable(null, new ByteBufferList)
      Thread.sleep(100L)
      mockedResponse.endCallback.onCompleted(null)
      a[TimeoutException] should be thrownBy Await.result(resp, 2.seconds)
    }

    scenario("multipart POST, connect (incl. sending of request) is very slow") {
      doPost("/post/multi_empty200", MultipartRequestContent(Seq("meep" -> new File(getClass.getResource("/emojis.txt").getPath))), false, 2.seconds, 10.seconds, client = clientWithDelay) match {
        case Response(HttpStatus(200, _), _, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("JSON POST, connect (incl. sending of request) is very slow") {
      a[TimeoutException] should be thrownBy doPost("/post/json_json200", jsonObject, false, 2.seconds, 10.seconds, client = clientWithDelay)
    }

  }

}
