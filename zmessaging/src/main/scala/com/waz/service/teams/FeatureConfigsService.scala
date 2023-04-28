package com.waz.service.teams

import com.waz.content.UserPreferences
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.sync.handler.FeatureConfigsSyncHandler
import com.waz.log.LogSE._
import com.waz.model.{ClassifiedDomainsConfig, ConferenceCallingFeatureConfig, FeatureConfigEvent, FeatureConfigUpdateEvent, FileSharingFeatureConfig, GuestLinksConfig, SelfDeletingMessagesFeatureConfig}
import com.waz.service.EventScheduler
import com.waz.service.EventScheduler.Stage
import com.waz.utils.JsonDecoder

import scala.concurrent.Future

trait FeatureConfigsService {
  def eventProcessingStage: Stage.Atomic
  def updateAppLock(): Future[Unit]
  def updateFileSharing(): Future[Unit]
  def updateSelfDeletingMessages(): Future[Unit]
  def updateConferenceCalling(): Future[Unit]
  def updateClassifiedDomains(): Future[Unit]
  def updateGuestLinks(): Future[Unit]
}

class FeatureConfigsServiceImpl(syncHandler: FeatureConfigsSyncHandler,
                                userPrefs: UserPreferences)
  extends FeatureConfigsService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def eventProcessingStage: Stage.Atomic = EventScheduler.Stage[FeatureConfigEvent] { (_, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> FeatureConfigsServiceImpl stage 1")

    Future.traverse(events)(processUpdateEvent)
  }

  private def processUpdateEvent(event: FeatureConfigEvent): Future[Unit] = event match {
    case FeatureConfigUpdateEvent("fileSharing", data) =>
      val fileSharing = JsonDecoder.decode[FileSharingFeatureConfig](data)
      verbose(l"File sharing enabled: ${fileSharing.isEnabled}")
      storeFileSharing(fileSharing)

    case FeatureConfigUpdateEvent("selfDeletingMessages", data) =>
      val selfDeletingMessages = JsonDecoder.decode[SelfDeletingMessagesFeatureConfig](data)
      verbose(l"Self deleting messages config: $selfDeletingMessages")
      storeSelfDeletingMessages(selfDeletingMessages)

    case FeatureConfigUpdateEvent("conferenceCalling", data) =>
      val conferenceCalling = JsonDecoder.decode[ConferenceCallingFeatureConfig](data)
      verbose(l"Conference calling enabled: ${conferenceCalling.isEnabled}")
      storeConferenceCallingConfig(conferenceCalling)

    case _ =>
      Future.successful(())
  }

  @inline
  private def withRecovery(f: => Future[Unit]): Future[Unit] =
    f.recover { case _ => Future.successful(()) }

  override def updateAppLock(): Future[Unit] = withRecovery {
    for {
      Some(appLock) <- syncHandler.fetchAppLock
      _             =  verbose(l"AppLock feature flag : $appLock")
      _             <- userPrefs(AppLockFeatureEnabled) := appLock.enabled
      _             <- userPrefs(AppLockForced)  := appLock.forced
      _             <- if (!appLock.enabled) userPrefs(AppLockEnabled) := false
                       else if (appLock.forced) userPrefs(AppLockEnabled) := true
                       else Future.successful(())
      _             <- userPrefs(AppLockTimeout) := appLock.timeout
    } yield ()
  }

  override def updateFileSharing(): Future[Unit] = withRecovery {
    for {
      Some(fileSharing) <- syncHandler.fetchFileSharing
      _                 =  verbose(l"FileSharing feature config : $fileSharing")
      _                 <- storeFileSharing(fileSharing)
    } yield ()
  }

  private def storeFileSharing(fileSharing: FileSharingFeatureConfig): Future[Unit] =
    for {
      existingValue <- userPrefs(FileSharingFeatureEnabled).apply()
      newValue      =  fileSharing.isEnabled
      _             <- userPrefs(FileSharingFeatureEnabled) := newValue
                       // Inform of changes.
      _             <- if (existingValue != newValue) userPrefs(ShouldInformFileSharingRestriction) := true
                       else Future.successful(())
    } yield ()


  override def updateConferenceCalling(): Future[Unit] = withRecovery {
    for {
      Some(conferenceCalling) <- syncHandler.fetchConferenceCalling
      _                       =  verbose(l"ConferenceCalling feature config: $conferenceCalling")
      _                       <- storeConferenceCallingConfig(conferenceCalling)
    } yield ()
  }

  private def storeConferenceCallingConfig(conferenceCallingFeatureConfig: ConferenceCallingFeatureConfig): Future[Unit] =
    for {
      existingValue <- userPrefs(ConferenceCallingFeatureEnabled).apply()
      newValue      =  conferenceCallingFeatureConfig.isEnabled
      _             <- userPrefs(ConferenceCallingFeatureEnabled) := newValue
                       // inform if we didn't have conference calls and now we do
      _             <- userPrefs(ShouldInformPlanUpgradedToEnterprise) := !existingValue && newValue
    } yield ()

  override def updateSelfDeletingMessages(): Future[Unit] = withRecovery {
    for {
      Some(selfDeleting) <- syncHandler.fetchSelfDeletingMessages
      _                  =  verbose(l"SelfDeletingMessages feature config: $selfDeleting")
      _                  <- storeSelfDeletingMessages(selfDeleting)
    } yield ()
  }

  private def storeSelfDeletingMessages(config: SelfDeletingMessagesFeatureConfig): Future[Unit] =
    for {
      wasEnabled        <- userPrefs(AreSelfDeletingMessagesEnabled).apply()
      lastKnownTimeout  <- userPrefs(SelfDeletingMessagesEnforcedTimeout).apply()
      isNowEnabled      =  config.isEnabled
      newTimeout        =  config.enforcedTimeoutInSeconds
      _                 <- userPrefs(AreSelfDeletingMessagesEnabled) := isNowEnabled
      _                 <- userPrefs(SelfDeletingMessagesEnforcedTimeout) := newTimeout
                           // Inform of new restrictions.
      _                 <- userPrefs(ShouldInformSelfDeletingMessagesChanged) :=
                             (wasEnabled != isNowEnabled || lastKnownTimeout != newTimeout)
    } yield ()

  override def updateClassifiedDomains(): Future[Unit] = withRecovery {
    for {
      Some(classifiedDomains) <- syncHandler.fetchClassifiedDomains
      _                       =  verbose(l"ClassifiedDomains feature config: $classifiedDomains")
      _                       <- storeClassifiedDomains(classifiedDomains)
    } yield ()
  }

  private def storeClassifiedDomains(classifiedDomains: ClassifiedDomainsConfig): Future[Unit] =
    userPrefs(ClassifiedDomains) := classifiedDomains.serialize

  override def updateGuestLinks(): Future[Unit] = withRecovery {
    for {
      Some(guestLinks) <- syncHandler.fetchGuestLinks
      _                =  verbose(l"GuestLinks feature config: $guestLinks")
      _                <- userPrefs(GuestLinks) := guestLinks.isEnabled
    } yield ()
  }
}
