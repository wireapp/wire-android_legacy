package com.waz.service.teams

import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AppLockFeatureConfig
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.handler.FeatureConfigsSyncHandler
import com.waz.testutils.TestUserPreferences

import scala.concurrent.Future
import scala.concurrent.duration._

class FeatureConfigsServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  private val userPrefs = new TestUserPreferences
  private val syncHandler = mock[FeatureConfigsSyncHandler]

  scenario("Fetch the AppLock feature flag and set properties") {
    userPrefs.setValue(AppLockEnabled, false)
    userPrefs.setValue(AppLockForced, false)
    userPrefs.setValue(AppLockTimeout, Some(30.seconds))

    (syncHandler.fetchAppLock _).expects().anyNumberOfTimes().returning(
      Future.successful(AppLockFeatureConfig(enabled = true, forced = true, timeout = Some(10.seconds)))
    )

    val service = createService
    result(service.updateAppLock())

    result(userPrefs(AppLockEnabled).apply()) shouldEqual true
    result(userPrefs(AppLockForced).apply()) shouldEqual true
    result(userPrefs(AppLockTimeout).apply()) shouldEqual Some(10.seconds)
  }

  scenario("When the AppLock feature flag is disabled, set the Disabled feature flag") {
    // The name here is a bit confusing.
    // "AppLockEnabled" in case of the property tells if the feature is on.
    // "enabled" in case of the feature flag tells if the feature flag (set in the backend) interferes with the feature settings.
    userPrefs.setValue(AppLockEnabled, true)
    userPrefs.setValue(AppLockForced, false)
    userPrefs.setValue(AppLockTimeout, Some(30.seconds))

    (syncHandler.fetchAppLock _).expects().anyNumberOfTimes().returning(
      Future.successful(AppLockFeatureConfig.Disabled)
    )

    val service = createService
    result(service.updateAppLock())

    result(userPrefs(AppLockEnabled).apply()) shouldEqual AppLockFeatureConfig.Disabled.enabled
    result(userPrefs(AppLockForced).apply()) shouldEqual AppLockFeatureConfig.Disabled.forced
    result(userPrefs(AppLockTimeout).apply()) shouldEqual AppLockFeatureConfig.Disabled.timeout
  }

  private def createService: FeatureConfigsService = new FeatureConfigsServiceImpl(syncHandler, userPrefs)
}
