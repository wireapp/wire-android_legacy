package com.waz.service.teams

import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AppLockFeatureFlag
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.handler.FeatureFlagsSyncHandler
import com.waz.testutils.TestUserPreferences

import scala.concurrent.Future
import scala.concurrent.duration._

class FeatureFlagsServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  private val userPrefs = new TestUserPreferences
  private val syncHandler = mock[FeatureFlagsSyncHandler]

  scenario("Fetch the AppLock feature flag and set properties") {
    userPrefs.setValue(AppLockEnabled, false)
    userPrefs.setValue(AppLockForced, false)
    userPrefs.setValue(AppLockTimeout, Some(30.seconds))

    (syncHandler.fetchAppLock _).expects().anyNumberOfTimes().returning(
      Future.successful(AppLockFeatureFlag(enabled = true, forced = true, timeout = Some(10.seconds)))
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
      Future.successful(AppLockFeatureFlag.Disabled)
    )

    val service = createService
    result(service.updateAppLock())

    result(userPrefs(AppLockEnabled).apply()) shouldEqual AppLockFeatureFlag.Disabled.enabled
    result(userPrefs(AppLockForced).apply()) shouldEqual AppLockFeatureFlag.Disabled.forced
    result(userPrefs(AppLockTimeout).apply()) shouldEqual AppLockFeatureFlag.Disabled.timeout
  }

  private def createService: FeatureFlagsService = new FeatureFlagsServiceImpl(syncHandler, userPrefs)
}
