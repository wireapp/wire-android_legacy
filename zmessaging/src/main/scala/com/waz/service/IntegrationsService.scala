/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.content.{AssetsStorage, ConversationStorage, MembersStorage, UsersStorage}
import com.waz.model._
import com.waz.service.conversation.ConversationsUiService
import com.waz.service.messages.MessagesService
import com.waz.sync.SyncResult.{Failure, Success}
import com.waz.sync.client.{ErrorOr, IntegrationsClient}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.control.NonFatal

trait IntegrationsService {
  def searchIntegrations(startWith: Option[String] = None): ErrorOr[Seq[IntegrationData]]
  def getIntegration(pId: ProviderId, iId: IntegrationId): ErrorOr[IntegrationData]

  def getOrCreateConvWithService(pId: ProviderId, serviceId: IntegrationId): ErrorOr[ConvId]

  def addBotToConversation(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[Either[ErrorResponse, Unit]]
  def removeBotFromConversation(cId: ConvId, botId: UserId): Future[Either[ErrorResponse, Unit]]
}

class IntegrationsServiceImpl(selfUserId:   UserId,
                              teamId:       Option[TeamId],
                              client:       IntegrationsClient,
                              assetStorage: AssetsStorage,
                              sync:         SyncServiceHandle,
                              users:        UsersStorage,
                              members:      MembersStorage,
                              messages:     MessagesService,
                              convs:        ConversationStorage,
                              convsUi:      ConversationsUiService,
                              syncRequests: SyncRequestService) extends IntegrationsService {
  implicit val ctx = Threading.Background

  override def searchIntegrations(startWith: Option[String] = None) =
    teamId match {
      case Some(tId) => client.searchTeamIntegrations(startWith, tId).future.flatMap {
        case Right(svs) => updateAssets(svs).map(svs => Right(svs))
        case Left(err) => Future.successful(Left(err))
      }
      case None => Future.successful(Right(Seq.empty[IntegrationData]))
    }

  override def getIntegration(pId: ProviderId, iId: IntegrationId) =
    client.getIntegration(pId, iId).future.flatMap {
      case Right((integration, asset)) =>
        updateAssets(Map(integration -> asset)).map(svs => Right(svs.head))
      case Left(err) => Future.successful(Left(err))
    }

  //Checks to see if the "new" asset we download for a bot isn't already in the database. If it is, we avoid creating a
  //new AssetData, and replace the AssetId on the IntegrationData with that of the asset previously put in the data base.
  //This has to be done first by checking the remote ids for any matches
  private def updateAssets(svs: Map[IntegrationData, Option[AssetData]]): Future[Seq[IntegrationData]] = {
    val services = svs.keys.toSet
    val assets = svs.values.flatten.toSet
    val remoteIds = assets.flatMap(_.remoteId)

    for {
      existingAssets <- assetStorage.findByRemoteIds(remoteIds)
      _              <- assetStorage.insertAll(assets -- assets.filter(_.remoteId.exists(existingAssets.flatMap(_.remoteId).contains)))
    } yield
      services.map { service =>
        val redundantAssetDataRId = svs(service).flatMap(_.remoteId)
        service.copy(asset = existingAssets.find(a => redundantAssetDataRId == a.remoteId).map(_.id).orElse(service.asset))
      }.toSeq
  }

  override def getOrCreateConvWithService(pId: ProviderId, serviceId: IntegrationId): ErrorOr[ConvId] = {
    def createConv: ErrorOr[ConvId] =
      for {
        (conv, syncId) <- convsUi.createGroupConversation(None)
        res <- syncRequests.await(syncId).flatMap {
          case Success =>
            for {
              postResult <- addBotToConversation(conv.id, pId, serviceId)
              service    <- members.getActiveUsers(conv.id)
              res        <- postResult.fold(Left(_), _ => Right(service.find(_ != selfUserId))) match {
                case Left(error)    => Future.successful(Left(error))
                case Right(Some(u)) => messages.addConnectRequestMessage(conv.id, selfUserId, u, "", "", fromSync = true).map(_ => Right(conv.id))
                case Right(_)       => Future.successful(Left(ErrorResponse.internalError("No user found for newly added service found - this shouldn't happen")))
              }
            } yield res
          case Failure(err) =>
            Future.successful(Left(err))
          case _ =>
            Future.successful(Left(internalError("Await should not have completed on SyncResult.Retry")))
        }
      } yield res

    (for {
      users      <- users.findUsersForService(serviceId).map(_.map(_.id)) //all created users with that service Id
      allConvs   <- members.getByUsers(users).map(_.map(_.convId)) //all conversations with one of the userIds for that service
      allMembers <- members.getByConvs(allConvs.toSet).map(_.map(m => m.convId -> m.userId))  //all member data for all of those conversations
      onlyUs = allMembers //the filtered member data where there are only 2 users, one of them is us, and the other is one of the possible service users
        .groupBy { case (c, _) => c }.map { case (cid, us) => cid -> us.map(_._2).toSet }
        .collect { case (c, us) if us.size == 2 && us.filterNot(_ == selfUserId).subsetOf(users) && us.contains(selfUserId) => c }
      convs   <- this.convs.getAll(onlyUs).map(_.flatten)
    } yield convs.find(c => teamId.exists(c.team.contains) && c.name.isEmpty).map(_.id)).recover {
      case NonFatal(_) => Option.empty[ConvId]
    }.flatMap {
      case Some(conv) => Future.successful(Right(conv))
      case _ => createConv
    }
  }

  // pId here is redundant - we can take it from our 'integrations' map
  override def addBotToConversation(cId: ConvId, pId: ProviderId, iId: IntegrationId): ErrorOr[Unit] =
    (for {
      syncId <- sync.postAddBot(cId, pId, iId)
      result <- syncRequests.await(syncId)
    } yield result).map {
      case Success        => Right({})
      case Failure(error) => Left(error)
      case _              => Left(internalError("Await should not have completed with SyncResult.Retry"))
    }

  override def removeBotFromConversation(cId: ConvId, botId: UserId): ErrorOr[Unit] =
    (for {
      syncId <- sync.postRemoveBot(cId, botId)
      result <- syncRequests.await(syncId)
    } yield result).map {
      case Success        => Right({})
      case Failure(error) => Left(error)
      case _              => Left(internalError("Await should not have completed with SyncResult.Retry"))
    }
}
