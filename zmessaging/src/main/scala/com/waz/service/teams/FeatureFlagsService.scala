package com.waz.service.teams

import com.waz.content.UserPreferences
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.sync.handler.FeatureFlagsSyncHandler
import com.waz.log.LogSE._

import scala.concurrent.Future

trait FeatureFlagsService {
  def updateAppLock(): Future[Unit]
  def updateFileSharing(): Future[Unit]
}

class FeatureFlagsServiceImpl(syncHandler: FeatureFlagsSyncHandler,
                              userPrefs: UserPreferences)
  extends FeatureFlagsService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def updateAppLock(): Future[Unit] =
    for {
      appLock <- syncHandler.fetchAppLock()
      _       =  verbose(l"AppLock feature flag : $appLock")
      _       <- userPrefs(AppLockFeatureEnabled) := appLock.enabled
      _       <- userPrefs(AppLockForced)  := appLock.forced
      _       <- if (!appLock.enabled) userPrefs(AppLockEnabled) := false
                 else if (appLock.forced) userPrefs(AppLockEnabled) := true
                 else Future.successful(())
      _       <- userPrefs(AppLockTimeout) := appLock.timeout
    } yield ()

  override def updateFileSharing(): Future[Unit] =
    for {
      fileSharing <- syncHandler.fetchFileSharing()
      _           =  verbose(l"FileSharing feature flag : $fileSharing")
      _           <- userPrefs(FileSharingFeatureEnabled) := fileSharing.enabled
    } yield ()
}
