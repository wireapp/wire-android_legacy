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
package com.waz.service.teams

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.utils.ContentChange.{Added, Removed, Updated}
import com.waz.content._
import com.waz.model.AccountDataOld.PermissionsMasks
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.{EventScheduler, SearchKey}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.{ContentChange, RichFuture}
import com.waz.utils.events.{AggregatingSignal, EventStream, RefreshingSignal, Signal}

import scala.collection.Seq
import scala.concurrent.Future
import scala.util.Right

//TODO - return Signals of the search results for UI??
trait TeamsService {

  def eventsProcessingStage: Stage.Atomic

  def searchTeamMembers(query: Option[SearchKey] = None, handleOnly: Boolean = false): Signal[Set[UserData]]

  val selfTeam: Signal[Option[TeamData]]

  def onTeamSynced(team: TeamData, members: Map[UserId, PermissionsMasks]): Future[Unit]

  def onMemberSynced(userId: UserId, permissions: PermissionsMasks): Future[Unit]

  def guests: Signal[Set[UserId]]
}

class TeamsServiceImpl(selfUser:           UserId,
                       teamId:             Option[TeamId],
                       teamStorage:        TeamsStorage,
                       userStorage:        UsersStorage,
                       convsStorage:       ConversationStorage,
                       convMemberStorage:  MembersStorage,
                       convsContent:       ConversationsContentUpdater,
                       sync:               SyncServiceHandle,
                       syncRequestService: SyncRequestService,
                       userPrefs:          UserPreferences) extends TeamsService {

  private implicit val dispatcher = SerialDispatchQueue()

  override val eventsProcessingStage: Stage.Atomic = EventScheduler.Stage[TeamEvent] { (_, events) =>
    verbose(s"Handling events: $events")
    import TeamEvent._

    val membersJoined  = events.collect { case MemberJoin(_, u) => u}.toSet
    val membersLeft    = events.collect { case MemberLeave(_, u)  => u}.toSet
    val membersUpdated = events.collect { case MemberUpdate(_, u)  => u}.toSet

    val convsCreated = events.collect { case ConversationCreate(_, id) => id }.toSet
    val convsDeleted = events.collect { case ConversationDelete(_, id) => id }.toSet
    for {
      _ <- RichFuture.processSequential(events.collect { case e:Update => e}) { case Update(id, name, icon, iconKey) => onTeamUpdated(id, name, icon, iconKey) }
      _ <- onMembersJoined(membersJoined -- membersLeft)
      _ <- onMembersLeft(membersLeft -- membersJoined)
      _ <- onMembersUpdated(membersUpdated)
      _ <- onConversationsCreated(convsCreated -- convsDeleted)
      _ <- onConversationsDeleted(convsDeleted -- convsCreated)
    } yield {}
  }

  override def searchTeamMembers(query: Option[SearchKey] = None, handleOnly: Boolean = false) = teamId match {
    case None => Signal.empty
    case Some(tId) => {

      val changesStream = EventStream.union[Seq[ContentChange[UserId, UserData]]](
        userStorage.onAdded.map(_.map(d => Added(d.id, d))),
        userStorage.onUpdated.map(_.map { case (prv, curr) => Updated(prv.id, prv, curr) }),
        userStorage.onDeleted.map(_.map(Removed(_)))
      )

      def load = query match {
        case Some(q) => userStorage.searchByTeam(tId, q, handleOnly)
        case None    => userStorage.getByTeam(Set(tId))
      }

      def userMatches(data: UserData) = data.teamId == teamId && (query match {
        case Some(q) =>
          if (handleOnly) data.handle.map(_.string).contains(q.asciiRepresentation)
          else q.isAtTheStartOfAnyWordIn(data.searchKey)
        case _ => true
      })

      new AggregatingSignal[Seq[ContentChange[UserId, UserData]], Set[UserData]](changesStream, load, { (current, changes) =>
        val added = changes.collect {
          case Added(_, data) if userMatches(data) => data
          case Updated(_, _, data) if userMatches(data) => data
        }.toSet

        val removed = changes.collect {
          case Removed(id) => id
          case Updated(id, _, data) if !userMatches(data) => id
        }.toSet

        current.filterNot(d => removed.contains(d.id) || added.exists(_.id == d.id)) ++ added
      })
    }
  }

  override lazy val selfTeam: Signal[Option[TeamData]] = teamId match {
    case None => Signal.const[Option[TeamData]](None)
    case Some(id) => new RefreshingSignal[Option[TeamData], Seq[TeamId]](
      CancellableFuture.lift(teamStorage.get(id)),
      teamStorage.onChanged.map(_.map(_.id))
    )
  }

  override lazy val guests = {
    def load(id: TeamId): Future[Set[UserId]] = for {
      convs       <- getTeamConversations.map(_.map(_.id))
      allUsers    <- convMemberStorage.getByConvs(convs).map(_.map(_.userId).toSet)
      teamMembers <- userStorage.getByTeam(Set(id)).map(_.map(_.id))
    } yield allUsers -- teamMembers

    val allChanges = {
      val ev1 = convMemberStorage.onUpdated.map(_.map(_._2.userId))
      val ev2 = convMemberStorage.onDeleted.map(_.map(_._1))
      EventStream.union(ev1, ev2)
    }

    teamId match {
      case None => Signal.const(Set.empty[UserId])
      case Some(id) => new RefreshingSignal[Set[UserId], Seq[UserId]](CancellableFuture.lift(load(id)), allChanges)
    }
  }

  override def onTeamSynced(team: TeamData, members: Map[UserId, PermissionsMasks]) = {
    verbose(s"onTeamSynced: team: $team \nmembers: $members")

    val memberIds = members.keys.toSet
    val selfPermissions = members.get(selfUser)

    for {
      _ <- teamStorage.insert(team)
      _ <- selfPermissions.fold(Future.successful({}))(onMemberSynced(selfUser, _))
      oldMembers <- userStorage.getByTeam(Set(team.id))
      _ <- userStorage.removeAll(oldMembers.map(_.id) -- memberIds)
      _ <- sync.syncUsers(memberIds).flatMap(syncRequestService.await)
      _ <- userStorage.updateAll2(memberIds, _.updated(teamId))
    } yield {}
  }

  override def onMemberSynced(userId: UserId, permissions: PermissionsMasks) = {
    import UserPreferences._
    if (userId == selfUser)
      for {
        _ <- userPrefs(SelfPermissions) := permissions._1
        _ <- userPrefs(CopyPermissions) := permissions._2
      } yield {}
    else Future.successful({})
  }

  private def onTeamUpdated(id: TeamId, name: Option[String], icon: Option[RAssetId], iconKey: Option[AESKey]) = {
    verbose(s"onTeamUpdated: $id, name: $name, icon: $icon, iconKey: $iconKey")
    teamStorage.update(id, team => team.copy (
      name    = name.getOrElse(team.name),
      icon    = icon.orElse(team.icon),
      iconKey = iconKey.orElse(team.iconKey)
    ))
  }

  private def onMembersJoined(members: Set[UserId]) = {
    verbose(s"onTeamMembersJoined: members: $members")
    for {
      _ <- sync.syncUsers(members).flatMap(syncRequestService.await)
      _ <- userStorage.updateAll2(members, _.updated(teamId))
    } yield {}
  }

  private def onMembersLeft(userIds: Set[UserId]) = {
    verbose(s"onTeamMembersLeft: users: $userIds")
    if (userIds.contains(selfUser)) {
      warn("Self user removed from team")
      Future.successful {}
    } else {
      for {
        _ <- userStorage.removeAll(userIds)
        _ <- removeUsersFromTeamConversations(userIds)
      } yield {}
    }
  }

  //So far, a member update just means we need to check the permissions for that user, and we only care about permissions
  //for the self user.
  private def onMembersUpdated(userIds: Set[UserId]) =
    if (userIds.contains(selfUser)) sync.syncTeamMember(selfUser) else Future.successful({})

  private def removeUsersFromTeamConversations(users: Set[UserId]) = {
    for {
      convs           <- getTeamConversations.map(_.map(_.id))
      membersToRemove = for (u <- users; c <- convs) yield (u, c)
      _               <- convMemberStorage.removeAll(membersToRemove)
    } yield {}
  }

  private def onConversationsCreated(convs: Set[RConvId]) = {
    verbose(s"onConversationsCreated: convs: $convs")
    if (convs.nonEmpty)
      for {
        convs <- Future.traverse(convs)(convsContent.convByRemoteId).map(_.collect { case Some(c) => c.id })
        _     <- sync.syncConversations(convs)
      } yield {}
    else Future.successful({})
  }

  private def onConversationsDeleted(convs: Set[RConvId]) = {
    verbose(s"onConversationsDeleted: convs: $convs")
    //TODO
    Future.successful({})
  }

  private def getTeamConversations = teamId match {
    case None => Future.successful(Set.empty)
    case Some(id) => verbose(s"searchTeamConversations: team: $teamId")
      import ConversationDataDao._
      convsStorage.find(_.team.contains(id), db => iterating(find(Team, Some(id))(db)), identity).map(_.toSet)
  }

}
