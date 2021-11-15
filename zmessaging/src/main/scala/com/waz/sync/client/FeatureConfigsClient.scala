package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.model.{AppLockFeatureConfig, FileSharingFeatureConfig, ConferenceCallingFeatureConfig, SelfDeletingMessagesFeatureConfig, TeamId}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

trait FeatureConfigsClient {
  def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureConfig]
  def getFileSharing: ErrorOrResponse[FileSharingFeatureConfig]
  def getSelfDeletingMessages: ErrorOrResponse[SelfDeletingMessagesFeatureConfig]
  def getConferenceCalling: ErrorOrResponse[ConferenceCallingFeatureConfig]
}

class FeatureConfigsClientImpl(implicit
                               urlCreator: UrlCreator,
                               httpClient: HttpClient,
                               authRequestInterceptor: AuthRequestInterceptor) extends FeatureConfigsClient {
  import FeatureConfigsClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._

  private implicit val AppLockFeatureConfigDeserializer: RawBodyDeserializer[AppLockFeatureConfig] =
    RawBodyDeserializer[JSONObject].map(json => AppLockFeatureConfig.Decoder(json))

  override def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureConfig] =
    Request.Get(relativePath = appLockPath(teamId))
      .withResultType[AppLockFeatureConfig]
      .withErrorType[ErrorResponse]
      .executeSafe

  override def getFileSharing: ErrorOrResponse[FileSharingFeatureConfig] =
    Request.Get(relativePath =  fileSharingPath)
    .withResultType[FileSharingFeatureConfig]
    .withErrorType[ErrorResponse]
    .executeSafe

  override def getSelfDeletingMessages: ErrorOrResponse[SelfDeletingMessagesFeatureConfig] =
    Request.Get(relativePath =  selfDeletingMessages)
    .withResultType[SelfDeletingMessagesFeatureConfig]
    .withErrorType[ErrorResponse]
    .executeSafe

  override def getConferenceCalling: ErrorOrResponse[ConferenceCallingFeatureConfig] =
    Request.Get(relativePath =  conferenceCallingPath)
      .withResultType[ConferenceCallingFeatureConfig]
      .withErrorType[ErrorResponse]
      .executeSafe
}

object FeatureConfigsClient {
  def appLockPath(teamId: TeamId): String = s"/teams/${teamId.str}/features/appLock"

  val basePath: String = "/feature-configs"
  val fileSharingPath: String = s"$basePath/fileSharing"
  val selfDeletingMessages: String = s"$basePath/selfDeletingMessages"

  val conferenceCallingPath: String = s"$basePath/conferenceCalling"
}
