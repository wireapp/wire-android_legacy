package com.waz.sync.client

import java.net.URL

import com.waz.api.impl.ErrorResponse
import com.waz.sync.client.SupportedApiClient.SupportedApiConfig
import com.waz.utils.CirceJSONSupport
import com.waz.utils.wrappers.URI
import com.waz.znet2.http.{HttpClient, Method, Request}

trait SupportedApiClient {
  def getSupportedApiVersions(baseUrl: URI): ErrorOrResponse[SupportedApiConfig]
}

final class SupportedApiClientImpl(implicit httpClient: HttpClient)
  extends SupportedApiClient with CirceJSONSupport {
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._

  override def getSupportedApiVersions(baseUrl: URI): ErrorOrResponse[SupportedApiConfig] = {
    val appended = baseUrl.buildUpon.appendPath("api-version").build
    Request.create(Method.Get, new URL(appended.toString))
      .withResultType[SupportedApiConfig]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object SupportedApiClient {
  final case class SupportedApiConfig(supported: Set[Int]) {
    lazy val max: Option[Int] = if (supported.nonEmpty) Some(supported.max) else None
  }
}
