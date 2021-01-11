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

  scenario("When the AppLock feature flag is disabled, don't change properties") {
    // The name here is a bit confusing.
    // "AppLockEnabled" in case of the property tells if the feature is on.
    // "enabled" in case of the feature flag tells if the feature flag (set in the backend) interferes with the feature settings.
    userPrefs.setValue(AppLockEnabled, true)
    userPrefs.setValue(AppLockForced, false)
    userPrefs.setValue(AppLockTimeout, Some(30.seconds))

    (syncHandler.fetchAppLock _).expects().anyNumberOfTimes().returning(
      Future.successful(AppLockFeatureFlag.Default)
    )

    val service = createService
    result(service.updateAppLock())

    result(userPrefs(AppLockEnabled).apply()) shouldEqual true
    result(userPrefs(AppLockForced).apply()) shouldEqual false
    result(userPrefs(AppLockTimeout).apply()) shouldEqual Some(30.seconds)
  }

  private def createService: FeatureFlagsService = new FeatureFlagsServiceImpl(syncHandler, userPrefs)
}
