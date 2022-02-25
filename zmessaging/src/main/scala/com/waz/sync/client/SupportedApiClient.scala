package com.waz.sync.client

import java.net.URL
import com.waz.api.impl.ErrorResponse
import com.waz.model.VersionBlacklist
import com.waz.sync.client.SupportedApiClient.SupportedApiConfig
import com.waz.utils.JsonDecoder.intArray
import com.waz.utils.{CirceJSONSupport, JsonDecoder}
import com.waz.utils.wrappers.URI
import com.waz.znet2.http.{HttpClient, Method, Request}
import org.json.JSONObject

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
      .recover {
        // If response is 404, then return SupportedApiConfig with only v0
            // TODO: how to test this?
        case e: ErrorResponse => if (e.code == 404) Right(SupportedApiClient.v0OnlyApiConfig) else Left(e)
      }
  }
}

object SupportedApiClient {

  val v0OnlyApiConfig = SupportedApiConfig(List(0), false, "")

  implicit lazy val Decoder: JsonDecoder[SupportedApiConfig] = new JsonDecoder[SupportedApiConfig] {
    override def apply(implicit js: JSONObject): SupportedApiConfig = {
      SupportedApiConfig(if(js.has("supported")) intArray(js.getJSONArray("supported")).toList else List.empty,
        js.optBoolean("federation"),
        js.optString("domain"))
    }
  }

  final case class SupportedApiConfig(supported: List[Int], federation: Boolean, domain: String) {
    lazy val max: Option[Int] = if (supported.nonEmpty) Some(supported.max) else None

    def highestCommonAPIVersion(versions: Set[Int]): Option[Int] =
      versions.intersect(supported.toSet).toList.sorted.lastOption
  }

}
