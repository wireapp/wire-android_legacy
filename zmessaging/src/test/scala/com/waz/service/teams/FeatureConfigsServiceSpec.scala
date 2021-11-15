package com.waz.service.teams

import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AppLockFeatureConfig, ConferenceCallingFeatureConfig, FeatureConfigUpdateEvent, FileSharingFeatureConfig, SelfDeletingMessagesFeatureConfig}
import com.waz.service.{EventPipeline, EventPipelineImpl, EventScheduler}
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.handler.FeatureConfigsSyncHandler
import com.waz.testutils.TestUserPreferences

import scala.concurrent.Future
import scala.concurrent.duration._

class FeatureConfigsServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  private val userPrefs = new TestUserPreferences
  private val syncHandler = mock[FeatureConfigsSyncHandler]

  private def createService: FeatureConfigsService = new FeatureConfigsServiceImpl(syncHandler, userPrefs)

  def createEventPipeline(service: FeatureConfigsService): EventPipeline = {
    val scheduler = new EventScheduler(Stage(Sequential)(service.eventProcessingStage))
    new EventPipelineImpl(Vector.empty, scheduler.enqueue)
  }

  scenario("Fetch the AppLock feature config and set properties") {
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

  scenario("When the AppLock feature config is disabled, set the Disabled feature flag") {
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

  scenario("Fetch the FileSharing feature config and set properties") {
    // Given
    val service = createService
    userPrefs.setValue(FileSharingFeatureEnabled, true)

    // Mock
    (syncHandler.fetchFileSharing _).expects().anyNumberOfTimes().returning(
      Future.successful(Some(FileSharingFeatureConfig("disabled")))
    )

    // When
    result(service.updateFileSharing())

    // Then
    result(userPrefs(FileSharingFeatureEnabled).apply()) shouldEqual false
  }

  scenario("Fetch the SelfDeletingMessage feature config and set properties") {
    // Given
    val service = createService
    userPrefs.setValue(AreSelfDeletingMessagesEnabled, false)
    userPrefs.setValue(SelfDeletingMessagesEnforcedTimeout, 0)

    // Mock
    (syncHandler.fetchSelfDeletingMessages _).expects().anyNumberOfTimes().returning(
      Future.successful(SelfDeletingMessagesFeatureConfig(isEnabled = true, 60))
    )

    // When
    result(service.updateSelfDeletingMessages())

    // Then
    result(userPrefs(AreSelfDeletingMessagesEnabled).apply()) shouldEqual true
    result(userPrefs(SelfDeletingMessagesEnforcedTimeout).apply()) shouldEqual 60
  }

  scenario("Process update event for FileSharing feature config") {
    // Given
    val service = createService
    val pipeline = createEventPipeline(service)
    val event = FeatureConfigUpdateEvent("fileSharing", "{ \"status\": \"disabled\" }")

    userPrefs.setValue(FileSharingFeatureEnabled, true)

    // When
    result(pipeline.apply(Seq(event)))

    // Then
    result(userPrefs(FileSharingFeatureEnabled).apply()) shouldEqual false
  }

  scenario("Process update event for SelfDeletingMessages feature config") {
    // Given
    val service = createService
    val pipeline = createEventPipeline(service)
    val event = FeatureConfigUpdateEvent("selfDeletingMessages", "{ \"status\": \"enabled\", \"config\": {\"enforcedTimeoutSeconds\": 60} }")

    userPrefs.setValue(AreSelfDeletingMessagesEnabled, false)
    userPrefs.setValue(SelfDeletingMessagesEnforcedTimeout, 0)

    // When
    result(pipeline.apply(Seq(event)))

    // Then
    result(userPrefs(AreSelfDeletingMessagesEnabled).apply()) shouldEqual true
    result(userPrefs(SelfDeletingMessagesEnforcedTimeout).apply()) shouldEqual 60
  }

  scenario("Fetch the ConferenceCalling feature config and set properties") {
    // Given
    val service = createService
    userPrefs.setValue(ConferenceCallingFeatureEnabled, true)

    // Mock
    (syncHandler.fetchConferenceCalling _).expects().anyNumberOfTimes().returning(
      Future.successful(ConferenceCallingFeatureConfig("disabled"))
    )

    // When
    result(service.updateConferenceCalling())

    // Then
    result(userPrefs(ConferenceCallingFeatureEnabled).apply()) shouldEqual false
  }

}
