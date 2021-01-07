package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{AppLockFeatureFlag, TeamId}
import com.waz.sync.client.FeatureFlagsClient

import scala.concurrent.Future

trait FeatureFlagsSyncHandler {
  def fetchAppLock(): Future[AppLockFeatureFlag]
}

class FeatureFlagsSyncHandlerImpl(teamId: Option[TeamId], client: FeatureFlagsClient)
  extends FeatureFlagsSyncHandler with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def fetchAppLock(): Future[AppLockFeatureFlag] = teamId match {
    case None =>
      Future.successful(AppLockFeatureFlag.Default)
    case Some(tId) =>
      client.getAppLock(tId).future.map {
        case Left(err) =>
          error(l"Unable to fetch AppLock feature flag: $err")
          AppLockFeatureFlag.Default
        case Right(appLock) =>
          appLock
      }
  }
}
