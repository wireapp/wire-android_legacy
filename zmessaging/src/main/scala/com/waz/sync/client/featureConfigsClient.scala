package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.model.{AppLockFeatureConfig, ConferenceCallingFeatureConfig, TeamId}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

trait featureConfigsClient {
  def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureConfig]
  def getConferenceCalling(): ErrorOrResponse[ConferenceCallingFeatureConfig]
}

class featureConfigsClientImpl(implicit
                               urlCreator: UrlCreator,
                               httpClient: HttpClient,
                               authRequestInterceptor: AuthRequestInterceptor) extends featureConfigsClient {
  import featureConfigsClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  private implicit val AppLockFeatureFlagDeserializer: RawBodyDeserializer[AppLockFeatureConfig] =
    RawBodyDeserializer[JSONObject].map(json => AppLockFeatureConfig.Decoder(json))

  override def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureConfig] =
    Request.Get(relativePath = path(teamId))
      .withResultType[AppLockFeatureConfig]
      .withErrorType[ErrorResponse]
      .executeSafe

  override def getConferenceCalling(): ErrorOrResponse[ConferenceCallingFeatureConfig] =
    Request.Get(relativePath =  conferenceCallingPath)
      .withResultType[ConferenceCallingFeatureConfig]
      .withErrorType[ErrorResponse]
      .executeSafe
}

object featureConfigsClient {
  def path(teamId: TeamId): String = s"/teams/${teamId.str}/features/appLock"
  val basePath: String = "/feature-configs"
  val conferenceCallingPath: String = s"$basePath/conferenceCalling"
}
