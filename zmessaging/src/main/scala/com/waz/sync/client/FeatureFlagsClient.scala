package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.model.{AppLockFeatureFlag, TeamId}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

trait FeatureFlagsClient {
  def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureFlag]
}

class FeatureFlagsClientImpl(implicit
                             urlCreator: UrlCreator,
                             httpClient: HttpClient,
                             authRequestInterceptor: AuthRequestInterceptor) extends FeatureFlagsClient {
  import FeatureFlagsClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  private implicit val AppLockFeatureFlagDeserializer: RawBodyDeserializer[AppLockFeatureFlag] =
    RawBodyDeserializer[JSONObject].map(json => AppLockFeatureFlag.Decoder(json))

  override def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureFlag] =
    Request.Get(relativePath = path(teamId))
      .withResultType[AppLockFeatureFlag]
      .withErrorType[ErrorResponse]
      .executeSafe
}

object FeatureFlagsClient {
  def path(teamId: TeamId): String = s"/teams/${teamId.str}/features/appLock"
}
