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

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import com.waz.RobolectricUtils
import com.waz.ZLog.LogTag
import com.waz.api.impl.ErrorResponse
import com.waz.service.BackendConfig
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.{ExponentialBackoff, returning}
import com.waz.znet.Response.{HttpStatus, ServerErrorStatus, SuccessHttpStatus}
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random
import scala.util.Random.nextInt

@Ignore
class ZNetClientSpec extends FeatureSpecLike with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils {

  val wireMockPort = 9000 + Random.nextInt(3000)
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(wireMockPort).withRootDirectory(getClass.getResource("/ZNetClientSpec").getPath))
  implicit val tag: LogTag = "ZNetClientSpec"
  import Threading.Implicits.Background

  var longRunningTime = 45.seconds
  var maxConcurrentRequests = 4
  var client: ZNetClient = _

  before {
    startMock()
    reset()
    client = new ZNetClientImpl(None, new AsyncClientImpl, BackendConfig("http://localhost:" + wireMockPort).baseUrl) {
//      new LoginClientImpl(new AsyncClientImpl, BackendConfig("http://localhost:" + wireMockPort), null)

      override def MaxConcurrentRequests = maxConcurrentRequests

      override def LongRunning = longRunningTime
    }
  }

  after {
    wireMockServer.stop()
    client.close()
  }

  private def startMock(): Unit = {
    wireMockServer.start()
    configureFor("localhost", wireMockPort)
  }

  override def useInstrumentation(name: String): Option[Boolean] =
    if (name.startsWith("org.mortbay")) Some(false) else super.useInstrumentation(name)

  val email = "test@test.com"
  val password = "password"

  val accessToken = "bb5373102ef17eb4d350d0bc84482a1357ab1a98a34b941f36c46a402b8fa62b.1.1394202093.a.333b8897-7950-42bd-964d-4f2dad285aef.10694619285061153311"

  def doGet(path: String, auth: Boolean = false, delay: FiniteDuration = 500.millis) = Await.result(client(Request[Unit]("GET", Some(path), requiresAuthentication = auth)), Duration(delay.toMillis, TimeUnit.MILLISECONDS))

  def doPost[A: ContentEncoder](path: String, data: A, auth: Boolean = false) = Await.result(client(Request("POST", Some(path), data = Some(data), requiresAuthentication = auth)), Duration(500, TimeUnit.MILLISECONDS))

  feature("Request retrying - exponential backoff") {

    scenario("Retry limited times") {
      val f = client(Request[Unit]("GET", Some("/get/404"), requiresAuthentication = false, retryPolicy = RetryPolicy(3)))
      Await.result(f, 2.seconds) match {
        case Response(HttpStatus(404 , _), _, _) => //fine
        case resp => fail(s"got unexpected response: $resp")
      }
    }

    scenario("Retry on network errors") {
      wireMockServer.stop()

      val f = client(Request[Unit]("GET", Some("/get/json"), requiresAuthentication = false, retryPolicy = RetryPolicy(4)))
      Thread.sleep(500)

      startMock()
      stubFor(get(urlEqualTo("/get/json")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"result":"ok"}""")))

      f should eventually(beMatching {
        case Response(HttpStatus(200 , _), JsonObjectResponse(js), _) => ()
      })
    }

    scenario("Do not retry on server error") {
      stubFor(get(urlEqualTo("/get/500")).willReturn(aResponse().withStatus(500).withBody("")))

      val f = client(Request[Unit]("GET", Some("/get/500"), requiresAuthentication = false, retryPolicy = new RetryPolicy() {
        override val backoff: ExponentialBackoff = new ExponentialBackoff(250.millis, 15.seconds)
        override def shouldRetry(status: Response.Status, retry: Int): Boolean = {
          status match {
            case ServerErrorStatus() => false
            case _ => true
          }
        }
      }))
      Await.result(f, 200.millis) match {
        case Response(HttpStatus(500 , _), _, _) => //fine
        case resp => fail(s"got unexpected response: $resp")
      }
    }
  }

  def verifyLoginRequested() =
    verify(postRequestedFor(urlEqualTo("/login?persist=true"))
      .withRequestBody(matching(s""".*"email":"$email".*"""))
      .withRequestBody(matching(s""".*"password":"$password".*"""))
      .withHeader("Content-Type", equalTo("application/json")))

  feature("Authentication") {

    scenario("Retry once on authentication failure - when token expires") {
      stubFor(post(urlEqualTo("/login?persist=true")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"expires_in":3600,"access_token":"token","token_type":"Bearer"}""")))

      stubFor(get(urlEqualTo("/self")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"result":"ok"}""")))

      doGet("/self", auth = true) match {
        case Response(SuccessHttpStatus(), _, _) => //fine
        case resp => fail(s"unexpected response: $resp")
      }

      reset()
      stubFor(post(urlEqualTo("/login?persist=true")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"expires_in":3600,"access_token":"updated","token_type":"Bearer"}""")))

      stubFor(get(urlEqualTo("/self")).willReturn(aResponse().withStatus(401)))

      doGet("/self", auth = true, delay = 5.seconds) match {
        case Response(HttpStatus(401, _), _, _) => //fine
        case resp => fail(s"unexpected response: $resp")
      }

      verifyLoginRequested()
      verify(getRequestedFor(urlEqualTo("/self")))
    }
  }

  feature("Get Request") {
    scenario("Invoke several requests") {
      stubFor(get(urlEqualTo("/self")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"result":"ok"}""")))

      for (i <- 1 to 10) {
        doGet("/self") match {
          case Response(HttpStatus(200, _), JsonObjectResponse(js), _) if js.getString("result") == "ok" => // fine
          case resp => fail(s"unexpected response: $resp")
        }
      }
    }
  }

  feature("Cancelling requests") {
    scenario("Invoke several requests and cancel some of them") {
      stubFor(post(urlEqualTo("/login?persist=true")).willReturn(aResponse().withFixedDelay(100).withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"expires_in":3600,"access_token":"token","token_type":"Bearer"}""")))

      val requests = for (i <- 1 to 4) yield client(Request[String]("POST", Some("/login?persist=true"), data = None, requiresAuthentication = false))
      requests.drop(2).foreach { _.cancel() }

      Await.result(CancellableFuture.sequence(requests.take(2)), 1.second).foreach(_.status.status shouldEqual 200)

      requests.drop(2) foreach { req =>
        intercept[CancelException] {
          Await.result(req, 1.second)
        }
      }
    }
  }

  feature("Long running requests do not block the queue") {
    scenario("Invoke four long running requests in safe intervals") {
      stubFor(get(urlEqualTo("/self")).willReturn(aResponse().withFixedDelay(4000).withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"result":"ok"}""")))

      longRunningTime = 500.millis
      maxConcurrentRequests = 1

      val a = client(Request[Unit]("GET", Some("/self"), requiresAuthentication = false))
      awaitUi(700.millis)

      val b = client(Request[Unit]("GET", Some("/self"), requiresAuthentication = false))
      awaitUi(700.millis)

      val c = client(Request[Unit]("GET", Some("/self"), requiresAuthentication = false))
      awaitUi(700.millis)

      val d = client(Request[Unit]("GET", Some("/self"), requiresAuthentication = false))

      Await.result(CancellableFuture.sequence(Seq(a, b, c, d)), 6.seconds).foreach(_.status.status shouldEqual 200)
    }
  }

  feature("Error handling") {

    scenario("catch timeout") {
      stubFor(get(urlEqualTo("/long")).willReturn(aResponse().withFixedDelay(1000).withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"result":"ok"}""")))
      val res = Await.result(client.withErrorHandling("delayed", Request[Unit]("GET", Some("/long"), requiresAuthentication = false, timeout = 500.millis)) { case Response(SuccessHttpStatus(), _, _) => () }, 5.seconds)
      res match {
        case Left(ErrorResponse(ErrorResponse.TimeoutCode, _, _)) => info(s"got cancel response: $res")
        case _ => fail(s"invalid response: $res")
      }
    }
  }

  scenario("Random requests") {
    stubFor(get(urlEqualTo("/short")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"result":"ok"}""")))
    stubFor(get(urlEqualTo("/long")).willReturn(aResponse().withFixedDelay(800).withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"result":"ok"}""")))
    stubFor(get(urlEqualTo("/flaky")).willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)))

    longRunningTime = 400.millis
    maxConcurrentRequests = 4

    def randomRequest: Future[Response] = CancellableFuture.delay(nextInt(10000).millis).flatMap { _ =>
      val diceRoll = nextInt(10)
      if (diceRoll < 4) client(Request[Unit]("GET", Some("/short"), requiresAuthentication = false))
      else if (diceRoll < 6) client(Request[Unit]("GET", Some("/long"), requiresAuthentication = false))
      else if (diceRoll < 7) client(Request[Unit]("GET", Some("/long"), requiresAuthentication = false, timeout = 500.millis, retryPolicy = RetryPolicy(2)))
      else if (diceRoll < 8) returning(client(Request[Unit]("GET", Some("/long"), requiresAuthentication = false))) { req =>
        CancellableFuture.delayed(nextInt(666).millis)(req.cancel)
      } recover {
        case c: CancelException => Response(Response.Cancelled)
      } else if (diceRoll < 9) client(Request[Unit]("GET", Some("/nonexistent"), requiresAuthentication = false, retryPolicy = RetryPolicy(1)))
      else client(Request[Unit]("GET", Some("/flaky"), requiresAuthentication = false, retryPolicy = RetryPolicy(1)))
    }

    val requests = (1 to 100).par.map(_ => randomRequest).seq

    Await.result(Future.sequence(requests), 2.minutes)
  }
}
