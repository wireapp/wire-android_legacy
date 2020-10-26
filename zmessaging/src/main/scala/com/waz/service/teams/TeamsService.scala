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

import com.waz.api.ErrorType
import com.waz.api.impl.ErrorResponse
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.{ConversationRolesService, ErrorsService, EventScheduler, SearchKey, SearchQuery, UserService}
import com.waz.sync.client.TeamsClient.TeamMember
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.wire.signals.CancellableFuture
import com.waz.utils.ContentChange.{Added, Removed, Updated}
import com.wire.signals.{AggregatingSignal, EventStream, RefreshingSignal, Signal}
import com.waz.utils.{ContentChange, RichFuture, RichInstant}
import org.threeten.bp.Instant

import scala.collection.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

//TODO - return Signals of the search results for UI??
trait TeamsService {

  def eventsProcessingStage: Stage.Atomic

  def searchTeamMembers(query: SearchQuery): Signal[Set[UserData]]

  val selfTeam: Signal[Option[TeamData]]

  def onTeamUpdated(team: TeamData): Future[Unit]

  def onTeamSynced(team: TeamData, members: Seq[TeamMember], roles: Set[ConversationRole]): Future[Unit]

  def onMemberSynced(member: TeamMember): Future[Unit]

  def guests: Signal[Set[UserId]]

  def deleteGroupConversation(teamId: TeamId, rConvId: RConvId): Future[SyncId]

  def onGroupConversationDeleteError(error: ErrorResponse, rConvId: RConvId): Future[Unit]

  def syncTeamData(): Future[SyncId]
}

class TeamsServiceImpl(selfUser:           UserId,
                       teamId:             Option[TeamId],
                       teamStorage:        TeamsStorage,
                       userService:        UserService,
                       userStorage:        UsersStorage,
                       convsStorage:       ConversationStorage,
                       convMemberStorage:  MembersStorage,
                       convsContent:       ConversationsContentUpdater,
                       convsService:       ConversationsService,
                       sync:               SyncServiceHandle,
                       syncRequestService: SyncRequestService,
                       userPrefs:          UserPreferences,
                       errorsService:      ErrorsService,
                       rolesService:       ConversationRolesService
                      ) extends TeamsService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private val shouldSyncTeam = userPrefs.preference(UserPreferences.ShouldSyncTeam)
  private val lastTeamUpdate = userPrefs.preference(UserPreferences.LastTeamUpdate)

  if (teamId.isDefined)
    for {
      shouldSync <- shouldSyncTeam()
      lastUpdate <- lastTeamUpdate()
    } if (shouldSync || lastUpdate.isBefore(Instant.now() - 24.hours)) {
      verbose(l"Syncing the team $teamId")
      sync.syncTeam().flatMap(_ => shouldSyncTeam := false) // lastTeamUpdate is refreshed in onTeamSynced
    }

  override val eventsProcessingStage: Stage.Atomic = EventScheduler.Stage[TeamEvent] { (_, events) =>
    verbose(l"Handling events: $events")
    import TeamEvent._

    val membersJoined  = events.collect { case MemberJoin(_, u)    => u }.toSet
    val membersLeft    = events.collect { case MemberLeave(_, u)   => u }.toSet
    val membersUpdated = events.collect { case MemberUpdate(_, u)  => u }.toSet

    for {
      _ <- RichFuture.traverseSequential(events.collect { case e: Update => e }) {
             case Update(id, name, icon) =>
               teamStorage.get(id).collect {
                 case Some(team) => onTeamUpdated(team.copy(name = name.getOrElse(team.name), icon = icon))
               }
           }
      _ <- onMembersJoined(membersJoined -- membersLeft)
      _ <- onMembersLeft(membersLeft -- membersJoined)
      _ <- onMembersUpdated(membersUpdated)
    } yield {}
  }

  override def searchTeamMembers(query: SearchQuery): Signal[Set[UserData]] = teamId match {
    case None => Signal.empty
    case Some(tId) =>

      val changesStream = EventStream.zip[Seq[ContentChange[UserId, UserData]]](
        userStorage.onAdded.map(_.map(d => Added(d.id, d))),
        userStorage.onUpdated.map(_.map { case (prv, curr) => Updated(prv.id, prv, curr) }),
        userStorage.onDeleted.map(_.map(Removed(_)))
      )

      def load =
        if (!query.isEmpty) userStorage.searchByTeam(tId, SearchKey(query.str), query.handleOnly)
        else userStorage.getByTeam(Set(tId))

      def userMatches(data: UserData) = data.isInTeam(teamId) && data.matchesQuery(query)

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

  override lazy val selfTeam: Signal[Option[TeamData]] = teamId match {
    case None =>
      Signal.const[Option[TeamData]](None)
    case Some(id) =>
      new RefreshingSignal(
        CancellableFuture.lift(teamStorage.get(id)),
        teamStorage.onChanged.map(_.filter(_.id == id)).filter(_.nonEmpty)
      )
  }

  // TODO: change to AggregatingSignal for better performance
  override lazy val guests: Signal[Set[UserId]] = {
    def load(id: TeamId): Future[Set[UserId]] =
      userStorage.contents.map(_.filter(_._2.teamId.contains(id)).keySet).head

    lazy val allChanges = {
      val ev1 = convMemberStorage.onUpdated.map(_.map(_._2.userId))
      val ev2 = convMemberStorage.onDeleted.map(_.map(_._1))
      EventStream.zip(ev1, ev2)
    }

    teamId match {
      case None => Signal.const(Set.empty[UserId])
      case Some(id) => new RefreshingSignal(CancellableFuture.lift(load(id)), allChanges)
    }
  }

  override def onTeamSynced(team: TeamData, members: Seq[TeamMember], roles: Set[ConversationRole]): Future[Unit] = {
    verbose(l"onTeamSynced: team: $team \nmembers: $members\n roles: $roles")

    val memberIds = members.map(_.user).toSet

    for {
      _          <- onTeamUpdated(team)
      oldMembers <- userStorage.getByTeam(Set(team.id))
      _          <- userStorage.updateAll2(oldMembers.map(_.id) -- memberIds, _.copy(deleted = true))
      _          <- sync.syncUsers(memberIds).flatMap(syncRequestService.await)
      _          <- userStorage.updateAll2(memberIds, _.copy(teamId = teamId, deleted = false))
      _          <- Future.sequence(members.map(onMemberSynced))
      _          <- rolesService.setDefaultRoles(roles)
    } yield {}
  }

  override def onMemberSynced(member: TeamMember): Future[Unit] =  {
    if (member.user == selfUser) member.permissions.foreach { ps =>
      import UserPreferences._
      for {
        _ <- userPrefs(SelfPermissions) := ps.self
        _ <- userPrefs(CopyPermissions) := ps.copy
      } yield ()
    }

    userStorage
      .update(member.user, _.copy(permissions = member.permissionMasks, createdBy = member.created_by))
      .map(_ => ())
  }

  override def deleteGroupConversation(tid: TeamId, rConvId: RConvId) = for {
    _      <- convsService.deleteConversation(rConvId)
    result <- sync.deleteGroupConversation(tid, rConvId)
  } yield { result }

  override def onTeamUpdated(team: TeamData): Future[Unit] = {
    verbose(l"onTeamUpdated: ${team.id}, name: ${team.name}, icon: ${team.icon}")
    teamStorage.insert(team).flatMap(_ => lastTeamUpdate := Instant.now())
  }

  private def onMembersJoined(members: Set[UserId]) = {
    verbose(l"onMembersJoined: members: $members")
    for {
      _ <- sync.syncUsers(members).flatMap(syncRequestService.await)
      _ <- sync.syncTeam().flatMap(syncRequestService.await)
      _ <- userStorage.updateAll2(members, _.copy(teamId = teamId, deleted = false))
    } yield {}
  }

  private def onMembersLeft(members: Set[UserId]) = {
    verbose(l"onMembersLeft: members: $members")
    if (members.contains(selfUser)) {
      warn(l"Self user removed from team")
      Future.successful {}
    } else
    // remove users from convs before deleting them so we still have their data when generating system messages
      convsService.deleteMembersFromConversations(members).flatMap(_ => userService.deleteUsers(members))
  }

  //So far, a member update just means we need to check the permissions for that user, and we only care about permissions
  //for the self user.
  private def onMembersUpdated(userIds: Set[UserId]) =
    if (userIds.contains(selfUser)) sync.syncTeamMember(selfUser) else Future.successful({})

  override def onGroupConversationDeleteError(err: ErrorResponse, rConvId: RConvId): Future[Unit] = {
    convsContent.convByRemoteId(rConvId).map { data =>
      Future.successful(errorsService.addErrorWhenActive(
        data match {
          case Some(convData) => ErrorData(ErrorType.CANNOT_DELETE_GROUP_CONVERSATION, err, convData.id)
          case None           => ErrorData(ErrorType.CANNOT_DELETE_GROUP_CONVERSATION, err)
        }
      ))
    }
  }

  override def syncTeamData(): Future[SyncId] = sync.syncTeamData()
}
