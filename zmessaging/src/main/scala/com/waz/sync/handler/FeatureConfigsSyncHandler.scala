package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{AppLockFeatureConfig, ConferenceCallingFeatureConfig, FileSharingFeatureConfig, SelfDeletingMessagesFeatureConfig, TeamId}
import com.waz.sync.client.{ErrorOrResponse, FeatureConfigsClient}

import scala.concurrent.Future

trait FeatureConfigsSyncHandler {
  def fetchAppLock: Future[Option[AppLockFeatureConfig]]
  def fetchFileSharing: Future[Option[FileSharingFeatureConfig]]
  def fetchSelfDeletingMessages: Future[Option[SelfDeletingMessagesFeatureConfig]]
  def fetchConferenceCalling: Future[Option[ConferenceCallingFeatureConfig]]
}

class FeatureConfigsSyncHandlerImpl(teamId: Option[TeamId], client: FeatureConfigsClient)
  extends FeatureConfigsSyncHandler with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def fetchAppLock: Future[Option[AppLockFeatureConfig]] = teamId match {
    case None =>
      Future.successful(Some(AppLockFeatureConfig.Default))
    case Some(tId) =>
      fetchFeatureFlag(() => client.getAppLock(tId), "AppLock")
  }

  override def fetchFileSharing: Future[Option[FileSharingFeatureConfig]] =
    fetchFeatureFlag(client.getFileSharing _, "FileSharing")

  override def fetchSelfDeletingMessages: Future[Option[SelfDeletingMessagesFeatureConfig]] =
    fetchFeatureFlag(client.getSelfDeletingMessages _, "SelfDeletingMessages")

  override def fetchConferenceCalling: Future[Option[ConferenceCallingFeatureConfig]] =
    fetchFeatureFlag(client.getConferenceCalling _, "ConferenceCalling")

  @inline
  private def fetchFeatureFlag[T](clientFunc: () => ErrorOrResponse[T], name: String): Future[Option[T]] =
    clientFunc().map {
      case Left(err) =>
        error(l"Unable to fetch $name feature flag: $err")
        None
      case Right(value) =>
        Some(value)
    }
}
