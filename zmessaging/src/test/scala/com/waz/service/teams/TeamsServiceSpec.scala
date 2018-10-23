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

import com.waz.content._
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.TestUserPreferences
import com.waz.utils.events.EventStream
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.Future

class TeamsServiceSpec extends AndroidFreeSpec {

  val selfUser =     UserId()
  val teamId =       Some(TeamId())
  val teamStorage =  mock[TeamsStorage]
  val accStorage =   mock[AccountsStorageOld]
  val userStorage =  mock[UsersStorage]
  val convsStorage = mock[ConversationStorage]
  val convMembers =  mock[MembersStorage]
  val convsContent = mock[ConversationsContentUpdater]
  val sync =         mock[SyncServiceHandle]
  val syncRequests = mock[SyncRequestService]
  val userPrefs =    new TestUserPreferences

  scenario("Complete team members signal updates on member add and remove") {
    val userStorageOnAdded    = EventStream[Seq[UserData]]()
    val userStorageOnUpdated  = EventStream[Seq[(UserData, UserData)]]()
    val userStorageOnDeleted  = EventStream[Seq[UserId]]()


    (userStorage.onAdded _).expects().once().returning(userStorageOnAdded)
    (userStorage.onUpdated _).expects().once().returning(userStorageOnUpdated)
    (userStorage.onDeleted _).expects().once().returning(userStorageOnDeleted)

    val initialTeamMembers = Set(
      UserData(UserId(), teamId, "user1", handle = Some(Handle()), searchKey = SearchKey.empty),
      UserData(UserId(), teamId, "user2", handle = Some(Handle()), searchKey = SearchKey.empty)
    )

    val newTeamMember = UserData(UserId(), teamId, "user3", handle = Some(Handle()), searchKey = SearchKey.empty)

    (userStorage.getByTeam _).expects(Set(teamId).flatten).once().returning(Future.successful(initialTeamMembers))

    val service = createService

    val res = service.searchTeamMembers().disableAutowiring() //disable autowiring to prevent multiple loads
    result(res.filter(_ == initialTeamMembers).head)

    userStorageOnAdded ! Seq(newTeamMember)
    result(res.filter(_ == (initialTeamMembers + newTeamMember)).head)


    userStorageOnDeleted ! Seq(newTeamMember.id)
    result(res.filter(_ == initialTeamMembers).head)
  }

  scenario("Search team members signal updates on member change") {
    val userStorageOnAdded    = EventStream[Seq[UserData]]()
    val userStorageOnUpdated  = EventStream[Seq[(UserData, UserData)]]()
    val userStorageOnDeleted  = EventStream[Seq[UserId]]()


    (userStorage.onAdded _).expects().once().returning(userStorageOnAdded)
    (userStorage.onUpdated _).expects().once().returning(userStorageOnUpdated)
    (userStorage.onDeleted _).expects().once().returning(userStorageOnDeleted)


    val member1 = UserData(UserId(), teamId, "user1", handle = Some(Handle()), searchKey = SearchKey.simple("user1"))
    val member2 = UserData(UserId(), teamId, "rick2", handle = Some(Handle()), searchKey = SearchKey.simple("rick2"))
    val member2Updated = member2.copy(name = "user2", searchKey = SearchKey.simple("user2"))

    (userStorage.searchByTeam _).expects(teamId.get, SearchKey.simple("user"), false).once().returning(Future.successful(Set(member1)))

    val service = createService

    val res = service.searchTeamMembers(Option(SearchKey.simple("user")), false).disableAutowiring() //disable autowiring to prevent multiple loads
    result(res.filter(_ == Set(member1)).head)

    userStorageOnUpdated ! Seq(member2 -> member2Updated)
    result(res.filter(_ == Set(member1, member2Updated)).head)


    userStorageOnUpdated ! Seq(member2Updated -> member2)
    result(res.filter(_ == Set(member1)).head)
  }

  scenario("Search team members signal doesn't update on non member add") {
    val userStorageOnAdded    = EventStream[Seq[UserData]]()
    val userStorageOnUpdated  = EventStream[Seq[(UserData, UserData)]]()
    val userStorageOnDeleted  = EventStream[Seq[UserId]]()


    (userStorage.onAdded _).expects().once().returning(userStorageOnAdded)
    (userStorage.onUpdated _).expects().once().returning(userStorageOnUpdated)
    (userStorage.onDeleted _).expects().once().returning(userStorageOnDeleted)

    val initialTeamMembers = Set(
      UserData(UserId(), teamId, "user1", handle = Some(Handle()), searchKey = SearchKey.empty),
      UserData(UserId(), teamId, "user2", handle = Some(Handle()), searchKey = SearchKey.empty)
    )

    val newTeamMember = UserData(UserId(), None, "user3", handle = Some(Handle()), searchKey = SearchKey.empty)

    (userStorage.getByTeam _).expects(Set(teamId).flatten).once().returning(Future.successful(initialTeamMembers))

    val service = createService

    val res = service.searchTeamMembers().disableAutowiring() //disable autowiring to prevent multiple loads
    result(res.filter(_ == initialTeamMembers).head)

    userStorageOnAdded ! Seq(newTeamMember)
    result(res.filter(_ == initialTeamMembers).head)


    userStorageOnDeleted ! Seq(newTeamMember.id)
    result(res.filter(_ == initialTeamMembers).head)
  }

  scenario("Search team members signal updates current values on member update") {
    val userStorageOnAdded    = EventStream[Seq[UserData]]()
    val userStorageOnUpdated  = EventStream[Seq[(UserData, UserData)]]()
    val userStorageOnDeleted  = EventStream[Seq[UserId]]()


    (userStorage.onAdded _).expects().once().returning(userStorageOnAdded)
    (userStorage.onUpdated _).expects().once().returning(userStorageOnUpdated)
    (userStorage.onDeleted _).expects().once().returning(userStorageOnDeleted)

    val constUser = UserData(UserId(), teamId, "user1", handle = Some(Handle()), searchKey = SearchKey.empty)
    val teamMemberToUpdate = UserData(UserId(), teamId, "user2", handle = Some(Handle()), searchKey = SearchKey.empty)
    val updatedTeamMember = teamMemberToUpdate.copy(name = "user3")

    val initialTeamMembers = Set(constUser, teamMemberToUpdate)
    val updatedTeamMembers = Set(constUser, updatedTeamMember)

    (userStorage.getByTeam _).expects(Set(teamId).flatten).once().returning(Future.successful(initialTeamMembers))

    val service = createService

    val res = service.searchTeamMembers().disableAutowiring() //disable autowiring to prevent multiple loads
    result(res.filter(_ == initialTeamMembers).head)

    userStorageOnUpdated ! Seq((teamMemberToUpdate, updatedTeamMember))
    result(res.filter(_ == updatedTeamMembers).head)
  }

  def createService = {
    new TeamsServiceImpl(selfUser, teamId, teamStorage, userStorage, convsStorage, convMembers, convsContent, sync, syncRequests, userPrefs)
  }

}
