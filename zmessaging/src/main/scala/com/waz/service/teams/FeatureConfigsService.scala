package com.waz.service.teams

import com.waz.content.UserPreferences
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.sync.handler.FeatureConfigsSyncHandler
import com.waz.log.LogSE._
import com.waz.model.ConferenceCallingFeatureConfig

import scala.concurrent.Future

trait FeatureConfigsService {
  def updateAppLock(): Future[Unit]
  def updateConferenceCalling(): Future[Unit]
}

class FeatureConfigsServiceImpl(syncHandler: FeatureConfigsSyncHandler,
                                userPrefs: UserPreferences)
  extends FeatureConfigsService with DerivedLogTag {
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

  override def updateConferenceCalling(): Future[Unit] =
    for {
      conferenceCalling <- syncHandler.fetchConferenceCalling()
      _           <- storeConferenceCallingConfig(conferenceCalling)
    } yield ()

  private def storeConferenceCallingConfig(conferenceCallingFeatureConfig: ConferenceCallingFeatureConfig): Future[Unit] =
    userPrefs(ConferenceCallingFeatureEnabled) := conferenceCallingFeatureConfig.isEnabled
}
