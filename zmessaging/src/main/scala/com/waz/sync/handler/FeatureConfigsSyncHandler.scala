package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{AppLockFeatureConfig, ConferenceCallingFeatureConfig, FileSharingFeatureConfig, SelfDeletingMessagesFeatureConfig, TeamId}
import com.waz.sync.client.FeatureConfigsClient

import scala.concurrent.Future

trait FeatureConfigsSyncHandler {
  def fetchAppLock(): Future[AppLockFeatureConfig]
  def fetchFileSharing(): Future[Option[FileSharingFeatureConfig]]
  def fetchSelfDeletingMessages(): Future[SelfDeletingMessagesFeatureConfig]
  def fetchConferenceCalling(): Future[ConferenceCallingFeatureConfig]
}

class FeatureConfigsSyncHandlerImpl(teamId: Option[TeamId], client: FeatureConfigsClient)
  extends FeatureConfigsSyncHandler with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def fetchAppLock(): Future[AppLockFeatureConfig] = teamId match {
    case None =>
      Future.successful(AppLockFeatureConfig.Default)
    case Some(tId) =>
      client.getAppLock(tId).future.map {
        case Left(err) =>
          error(l"Unable to fetch AppLock feature flag: $err")
          AppLockFeatureConfig.Default
        case Right(appLock) =>
          appLock
      }
  }

  override def fetchFileSharing(): Future[Option[FileSharingFeatureConfig]] =
    client.getFileSharing().map {
      case Left(err) =>
        error(l"Unable to fetch FileSharing feature flag: $err")
        None
      case Right(fileSharing) =>
        Some(fileSharing)
    }

  override def fetchSelfDeletingMessages(): Future[SelfDeletingMessagesFeatureConfig] =
    client.getSelfDeletingMessages().map {
      case Left(err) =>
        error(l"Unable to fetch SelfDeletingMessages feature flag: $err")
        SelfDeletingMessagesFeatureConfig.Default
      case Right(selfDeletingMessages) =>
        selfDeletingMessages
    }

  override def fetchConferenceCalling(): Future[ConferenceCallingFeatureConfig] =
    client.getConferenceCalling().map {
      case Left(err) =>
        error(l"Unable to fetch ConferenceCalling feature flag: $err")
        ConferenceCallingFeatureConfig.Default
      case Right(conferenceCalling) =>
        conferenceCalling
    }
}
