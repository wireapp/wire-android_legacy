package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.model.{AppLockFeatureFlag, FileSharingFeatureFlag, TeamId}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

trait FeatureFlagsClient {
  def getAppLock(teamId: TeamId): ErrorOrResponse[AppLockFeatureFlag]
  def getFileSharing(): ErrorOrResponse[FileSharingFeatureFlag]
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
    Request.Get(relativePath = appLockPath(teamId))
      .withResultType[AppLockFeatureFlag]
      .withErrorType[ErrorResponse]
      .executeSafe

  override def getFileSharing(): ErrorOrResponse[FileSharingFeatureFlag] =
    Request.Get(relativePath =  fileSharingPath)
    .withResultType[FileSharingFeatureFlag]
    .withErrorType[ErrorResponse]
    .executeSafe
}

object FeatureFlagsClient {
  def appLockPath(teamId: TeamId): String = s"/teams/${teamId.str}/features/appLock"

  val basePath: String = "/feature-configs"
  val fileSharingPath: String = s"$basePath/fileSharing"

}
