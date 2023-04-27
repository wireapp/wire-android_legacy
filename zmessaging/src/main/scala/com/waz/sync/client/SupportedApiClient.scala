package com.waz.sync.client

import java.net.URL
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.SyncId
import com.waz.utils.JsonDecoder.intArray
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.utils.wrappers.URI
import com.waz.znet2.http.{HttpClient, Method, Request}
import com.wire.signals.CancellableFuture
import org.json.{JSONArray, JSONObject}

trait SupportedApiClient {
  def getSupportedApiVersions(baseUrl: URI): ErrorOrResponse[SupportedApiConfig]
}

final class SupportedApiClientImpl(implicit httpClient: HttpClient)
  extends SupportedApiClient with DerivedLogTag {
  import com.waz.znet2.http.HttpClient.AutoDerivationOld._
  import HttpClient.dsl._
  import SupportedApiConfig._

  override def getSupportedApiVersions(baseUrl: URI): ErrorOrResponse[SupportedApiConfig] = {
    if(baseUrl == null || baseUrl.getHost == null || baseUrl.getHost.isEmpty) { // this might be the case before we switch to another backend
      return CancellableFuture(Right(v0OnlyApiConfig))
    }
    val appended = baseUrl.toString.stripSuffix("/") + "/api-version"
    // unfortunately, the nicer code:
    //      val appended = baseUrl.buildUpon.appendPath("api-version").build
    // causes a `NoSuchMethodException` when run in tests
    Request.create(Method.Get, new URL(appended))
      .withResultType[SupportedApiConfig]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .map {
        case Right(value) => Right(value)
        case Left(error) if error.code == 404 => Right(SupportedApiConfig.v0OnlyApiConfig)
        case Left(error) => Left(error)
      }
  }
}

final case class SupportedApiConfig(supported: List[Int], federation: Boolean, domain: String) {

  def highestCommonAPIVersion(versions: Set[Int] = SupportedApiConfig.supportedBackendAPIVersions): Option[Int] =
    versions.intersect(supported.toSet).toList.sorted.lastOption

  def serialized: JSONObject = SupportedApiConfig.Encoder(this)

  override def toString: String = serialized.toString()
}

object SupportedApiConfig {

  /**
    * List of backend API versions that this application can support. These will be negotiated with the
    * backend to find a common version to use. This list should be updated every time code is added to support
    * a new version or removed to sunset an old version.
    */
  val supportedBackendAPIVersions = Set(0, 1)

  def fromString(string: String): Option[SupportedApiConfig] = {
    try {
      Some(SupportedApiConfig.Decoder(new JSONObject(string)))
    }
    catch {
      case _: Throwable => None
    }
  }

  implicit val Decoder: JsonDecoder[SupportedApiConfig] = new JsonDecoder[SupportedApiConfig] {
    override def apply(implicit js: JSONObject): SupportedApiConfig = {
      SupportedApiConfig(if(js.has("supported")) intArray(js.getJSONArray("supported")).toList else List.empty,
        js.optBoolean("federation"),
        js.optString("domain"))
    }
  }

  implicit val Encoder: JsonEncoder[SupportedApiConfig] = new JsonEncoder[SupportedApiConfig] {
    override def apply(v: SupportedApiConfig): JSONObject = new JSONObject()
      .put("supported", new JSONArray(v.supported.toArray))
      .put("federation", v.federation)
      .put("domain", v.domain)
  }

  val v0OnlyApiConfig: SupportedApiConfig = SupportedApiConfig(List(0), false, "")

}
