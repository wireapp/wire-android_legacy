package com.waz.service.teams

import com.waz.content.UserPreferences
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.sync.handler.FeatureFlagsSyncHandler
import com.waz.log.LogSE._

import scala.concurrent.Future

trait FeatureFlagsService {
  def updateAppLock(): Future[Unit]
}

class FeatureFlagsServiceImpl(syncHandler: FeatureFlagsSyncHandler,
                              userPrefs: UserPreferences)
  extends FeatureFlagsService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def updateAppLock(): Future[Unit] = syncHandler.fetchAppLock().map {
    case appLock if appLock.enabled =>
      verbose(l"AppLock feature flag enabled: $appLock")
      for {
        _ <- if (appLock.forced) userPrefs(AppLockEnabled) := true else Future.successful(())
        _ <- userPrefs(AppLockForced) := appLock.forced
        _ <- userPrefs(AppLockTimeout) := appLock.timeout
      } yield ()
    case _ =>
      verbose(l"AppLock feature flag disabled")
  }
}
