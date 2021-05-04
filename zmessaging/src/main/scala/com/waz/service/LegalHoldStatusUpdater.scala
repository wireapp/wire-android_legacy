package com.waz.service

import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.model.{ConvId, ConversationData, Messages, RConvId}
import com.waz.model.otr.UserClients

import scala.concurrent.Future

trait LegalHoldStatusUpdater {
  def updateStatusFromMessageHint(convId: RConvId,
                                  messageStatus: Messages.LegalHoldStatus): Future[Unit]
}

class DummyLegalHoldStatusUpdater extends LegalHoldStatusUpdater {
  override def updateStatusFromMessageHint(convId: RConvId,
                                           messageStatus: Messages.LegalHoldStatus): Future[Unit] =
    Future.successful(())
}

// Updates the legal hold status of conversations when new
// legal hold devices are discovered.

class LegalHoldStatusUpdaterImpl(clientsStorage: OtrClientsStorage,
                                 convStorage: ConversationStorage,
                                 membersStorage: MembersStorage,
                                 userService: UserService) extends LegalHoldStatusUpdater {

  import com.waz.threading.Threading.Implicits.Background

  // When clients are added, updated, or deleted...
  clientsStorage.onChanged.foreach(onClientsChanged)

  // When a participant is added...
  membersStorage.onAdded.foreach(members => updateLegalHoldStatus(members.map(_.convId)))

  // When a participant is removed...
  membersStorage.onDeleted.foreach(members => updateLegalHoldStatus(members.map(_._2)))

  def onClientsChanged(userClients: Seq[UserClients]): Future[Unit] = {
    val userIds = userClients.map(_.id)

    for {
      convs <- Future.traverse(userIds)(membersStorage.getActiveConvs).map(_.flatten)
      _     <- updateLegalHoldStatus(convs)
    } yield ()
  }

  def updateLegalHoldStatus(convIds: Seq[ConvId]): Future[Unit] =
    Future.traverse(convIds.distinct)(updateLegalHoldStatus).map(_ => ())

  private def updateLegalHoldStatus(convId: ConvId): Future[Unit] = {
    for {
      participants      <- membersStorage.getActiveUsers(convId)
      clients           <- Future.traverse(participants)(clientsStorage.getClients).map(_.flatten)
      detectedLegalHold = clients.exists(_.isLegalHoldDevice)
      _                 <- convStorage.updateAll2(Seq(convId), _.withNewLegalHoldStatus(detectedLegalHold))
    } yield ()
  }

  override def updateStatusFromMessageHint(convId: RConvId,
                                           messageStatus: Messages.LegalHoldStatus): Future[Unit] = {
    convStorage.getByRemoteId(convId).flatMap {
      case Some(conv) => statusUpdate(conv, messageStatus) match {
          case Some(updater) =>
            convStorage.update(conv.id, updater)
              .flatMap(_ => userService.syncClients(conv.id))
          case None =>
            Future.successful(())
        }
      case None =>
        Future.successful(())
    }
  }

  private def statusUpdate(conv: ConversationData,
                           messageStatus: Messages.LegalHoldStatus): Option[ConversationData => ConversationData] =
    (conv.isUnderLegalHold, messageStatus) match {
      case (false, Messages.LegalHoldStatus.ENABLED) =>
        Some(_.withNewLegalHoldStatus(detectedLegalHoldDevice = true))
      case (true, Messages.LegalHoldStatus.DISABLED) =>
        Some(_.withNewLegalHoldStatus(detectedLegalHoldDevice = false))
      case _ =>
        None
    }



}
