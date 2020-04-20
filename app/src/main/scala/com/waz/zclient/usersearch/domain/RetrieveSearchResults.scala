/**
  * Wire
  * Copyright (C) 2019 Wire Swiss GmbH
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */
package com.waz.zclient.usersearch.domain

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.{SearchUserListState, Tab}
import com.waz.zclient.usersearch.listitems._
import com.waz.zclient.{Injectable, Injector}

import scala.collection.mutable

class RetrieveSearchResults()(implicit injector: Injector, eventContext: EventContext) extends Injectable
  with DerivedLogTag {

  import SearchViewItem._
  import SectionViewItem._

  private val userAccountsController    = inject[UserAccountsController]
  private val searchController          = inject[SearchController]

  private var collapsedContacts         = true
  private var collapsedGroups           = true

  private var team                      = Option.empty[TeamData]
  private var topUsers                  = Seq.empty[UserData]
  private var localResults              = Seq.empty[UserData]
  private var conversations             = Seq.empty[ConversationData]
  private var directoryResults          = Seq.empty[UserData]
  private var integrations              = Seq.empty[IntegrationData]
  private var currentUser               = Option.empty[UserData]
  private var currentUserCanAddServices = false
  private var noServices                = false

  val resultsData = Signal(List.empty[SearchViewItem])

  (for {
    curUser  <- userAccountsController.currentUser
    teamData <- userAccountsController.teamData
    isAdmin  <- userAccountsController.isAdmin
    results  <- searchController.searchUserOrServices
  } yield (curUser, teamData, isAdmin, results)).onUi {
    case (curUser, teamData, isAdmin, results) =>
      verbose(l"Search user list state: $results")
      team = teamData
      currentUserCanAddServices = isAdmin
      currentUser = curUser

      results match {
        case SearchUserListState.Users(search) =>
          topUsers = search.top
          localResults = search.local
          conversations = search.convs
          directoryResults = search.dir
        case _                                 =>
          topUsers = Seq.empty
          localResults = Seq.empty
          conversations = Seq.empty
          directoryResults = Seq.empty
      }

      noServices = results match {
        case SearchUserListState.NoServices => true
        case _                              => false
      }

      integrations = results match {
        case SearchUserListState.Services(svs) => svs.toIndexedSeq.sortBy(_.name)
        case _                                 => IndexedSeq.empty
      }
      updateMergedResults()
  }

  def expandGroups(): Unit = {
    collapsedGroups = false
    updateMergedResults()
  }

  def expandContacts(): Unit = {
    collapsedContacts = false
    updateMergedResults()
  }

  private def updateMergedResults(): Unit = {
    val mergedResult = mutable.ListBuffer[SearchViewItem]()

    val teamName = team.map(_.name).getOrElse(Name.Empty)

    def addTopPeople(): Unit = if (topUsers.nonEmpty) {
      mergedResult += SectionViewItem(TopUsersSection, 0)
      mergedResult += TopUserViewItem(0, topUsers)
    }

    def addContacts(): Unit = {
      val directoryTeamMembers = currentUser.map(_.teamId) match {
        case Some(teamId) => directoryResults.filter(_.teamId == teamId)
        case None         => Nil
      }
      val contactsList = (localResults ++ directoryTeamMembers).distinctBy(_.id)
      if (contactsList.nonEmpty) {
        mergedResult += SectionViewItem(ContactsSection, 0, teamName)

        val contactsSection = contactsList.zipWithIndex.map { case (user, index) =>
          ConnectionViewItem(index, user, team.map(_.id), connected = true)
        }

        val shouldCollapse = searchController.filter.currentValue.exists(_.nonEmpty) && collapsedContacts && contactsSection.size > CollapsedContacts

        mergedResult ++= contactsSection.sortBy(_.name.str).take(if (shouldCollapse) CollapsedContacts else contactsSection.size)
        if (shouldCollapse)
          mergedResult += ExpandViewItem(ContactsSection, 0, contactsList.size)
      }
    }

    def addGroupConversations(): Unit = if (conversations.nonEmpty) {
      mergedResult += SectionViewItem(GroupConversationsSection, 0, teamName)

      val shouldCollapse = collapsedGroups && conversations.size > CollapsedGroups

      mergedResult ++= conversations.zipWithIndex.map { case (conv, index) =>
        GroupConversationViewItem(index, conv)
      }.take(if (shouldCollapse) CollapsedGroups else conversations.size)
      if (shouldCollapse)
        mergedResult += ExpandViewItem(GroupConversationsSection, 0, conversations.size)
    }

    def addConnections(): Unit = {
      val directoryExternalMembers = currentUser.map(_.teamId) match {
        case Some(teamId) => directoryResults.filterNot(_.teamId == teamId)
        case None         => Nil
      }
      if (directoryExternalMembers.nonEmpty) {
        mergedResult += SectionViewItem(DirectorySection, 0)
        mergedResult ++= directoryResults.zipWithIndex.map { case (user, index) =>
          ConnectionViewItem(index, user, team.map(_.id), connected = false)
        }
      }
    }

    def addIntegrations(): Unit = {
      if (integrations.nonEmpty) {
        mergedResult ++= integrations.zipWithIndex.map { case (integration, index) =>
          IntegrationViewItem(index, integration)
        }
      }
    }

    def addGroupCreationButton(): Unit =
      mergedResult += TopUserButtonViewItem(NewConversation, TopUsersSection, 0)

    def addGuestRoomCreationButton(): Unit =
      mergedResult += TopUserButtonViewItem(NewGuestRoom, TopUsersSection, 0)

    def addManageServicesButton(): Unit =
      mergedResult += TopUserButtonViewItem(ManageServices, TopUsersSection, 0)

    if (team.isDefined) {
      if (searchController.tab.currentValue.contains(Tab.Services)) {
        if (currentUserCanAddServices && !noServices) addManageServicesButton()
        addIntegrations()
      } else {
        if (searchController.filter.currentValue.forall(_.isEmpty) && !userAccountsController.isExternal.currentValue.get) {
          addGroupCreationButton()
          addGuestRoomCreationButton()
        }
        addContacts()
        addGroupConversations()
        addConnections()
      }
    } else {
      if (searchController.filter.currentValue.forall(_.isEmpty) && !userAccountsController.isExternal.currentValue.get)
        addGroupCreationButton()
      addTopPeople()
      addContacts()
      addGroupConversations()
      addConnections()
    }

    resultsData ! mergedResult.toList
  }
}
