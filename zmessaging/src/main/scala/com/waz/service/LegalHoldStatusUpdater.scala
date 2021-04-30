package com.waz.service

import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.otr.UserClients
import com.waz.model.{ConvId, ConversationData}
import com.waz.model.ConversationData.LegalHoldStatus._

import scala.concurrent.Future

trait LegalHoldStatusUpdater

class DummyLegalHoldStatusUpdater extends LegalHoldStatusUpdater

// Updates the legal hold status of conversations when new
// legal hold devices are discovered.

class LegalHoldStatusUpdaterImpl(clientsStorage: OtrClientsStorage,
                                 convStorage: ConversationStorage,
                                 membersStorage: MembersStorage) extends LegalHoldStatusUpdater with DerivedLogTag {

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
      _                 <- convStorage.updateAll2(Seq(convId), { conv =>
                             update(conv, detectedLegalHold)
                           })
    } yield ()
  }

  def update(conv: ConversationData, detectedLegalHoldDevice: Boolean): ConversationData = {
    val status = (conv.legalHoldStatus, detectedLegalHoldDevice) match {
      case (Disabled, true)         => PendingApproval
      case (PendingApproval, false) => Disabled
      case (Enabled, false)         => Disabled
      case (existingStatus, _)      => existingStatus
    }

    verbose(l"Updating ${conv.name} from status ${conv.legalHoldStatus} to $status")
    conv.copy(legalHoldStatus = status)
  }

}
