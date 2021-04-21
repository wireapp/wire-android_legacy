package com.waz.service

import com.waz.api.IConversation.LegalHoldStatus._
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.otr.UserClients
import com.waz.model.{ConvId, ConversationData}

import scala.concurrent.Future

// Updates the legal hold status of conversations when new
// legal hold devices are discovered.

class LegalHoldStatusUpdater(clientsStorage: OtrClientsStorage,
                             convStorage: ConversationStorage,
                             membersStorage: MembersStorage) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  // When clients are added, updated, or deleted...
  clientsStorage.onChanged { userClients =>
    verbose(l"on clients changed")
    onClientsChanged(userClients)
  }

  // When a participant is added...
  membersStorage.onAdded { members =>
    verbose(l"on members added")
    val convIds = members.map(_.convId)
    updateLegalHoldStatus(convIds)
  }

  // When a participant is removed...
  membersStorage.onDeleted { members =>
    verbose(l"on members deleted")
    val convIds = members.map(_._2)
    updateLegalHoldStatus(convIds)
  }

  def onClientsChanged(userClients: Seq[UserClients]): Future[Unit] = {
    val userIds = userClients.map(_.id)

    for {
      convs <- Future.traverse(userIds)(membersStorage.getActiveConvs).map(_.flatten)
      _     <- updateLegalHoldStatus(convs)
    } yield ()
  }

  def updateLegalHoldStatus(convIds: Seq[ConvId]): Future[Unit] =
    for {
      _ <- Future.traverse(convIds.distinct)(updateLegalHoldStatus)
    } yield ()

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
      case (DISABLED, true)          => PENDING_APPROVAL
      case (PENDING_APPROVAL, false) => DISABLED
      case (ENABLED, false)          => DISABLED
      case (existingStatus, _)       => existingStatus
    }

    verbose(l"Updating ${conv.name} from status ${conv.legalHoldStatus} to $status")
    conv.copy(legalHoldStatus = status)
  }

}
