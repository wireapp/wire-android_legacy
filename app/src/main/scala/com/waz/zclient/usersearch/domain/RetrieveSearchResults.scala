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

import androidx.lifecycle.{LiveData, MutableLiveData}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.utils.events.EventContext
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

  private val userAccountsController = inject[UserAccountsController]
  private val searchController       = inject[SearchController]

  private var mergedResult           = mutable.ListBuffer[SearchViewItem]()
  private var collapsedContacts      = true
  private var collapsedGroups        = true

  private var team                   = Option.empty[TeamData]
  private var topUsers               = Seq.empty[UserData]
  private var localResults           = Seq.empty[UserData]
  private var conversations          = Seq.empty[ConversationData]
  private var directoryResults       = Seq.empty[UserData]
  private var integrations           = Seq.empty[IntegrationData]
  private var currentUser            = Option.empty[UserData]
  private var currentUserIsAdmin     = false
  private var noServices             = false

  private val resultsLiveData: MutableLiveData[mutable.ListBuffer[SearchViewItem]] =
    new MutableLiveData[mutable.ListBuffer[SearchViewItem]]

  def resultsData: LiveData[mutable.ListBuffer[SearchViewItem]] = resultsLiveData

  (for {
    curUser  <- userAccountsController.currentUser
    teamData <- userAccountsController.teamData
    isAdmin  <- userAccountsController.isAdmin
    results  <- searchController.searchUserOrServices
  } yield (curUser, teamData, isAdmin, results)).onUi {
    case (curUser, teamData, isAdmin, results) =>

      verbose(l"Search user list state: $results")
      team = teamData
      currentUserIsAdmin = isAdmin
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
    mergedResult = mutable.ListBuffer[SearchViewItem]()

    val teamName = team.map(_.name).getOrElse(Name.Empty)

    def addTopPeople(): Unit = {
      if (topUsers.nonEmpty) {
        val topUserSectionHeader = new SectionViewItem(SectionViewModel(TopUsersSection, 0))
        val topUsersListItem = new TopUserViewItem(TopUserViewModel(0, topUsers))
        mergedResult = mergedResult ++ Seq(topUserSectionHeader)
        mergedResult = mergedResult ++ Seq(topUsersListItem)
      }
    }

    def addContacts(): Unit = {
      if (localResults.nonEmpty) {
        val contactsSectionHeader = new SectionViewItem(SectionViewModel(ContactsSection, 0, teamName))
        mergedResult = mergedResult ++ Seq(contactsSectionHeader)
        var contactsSection = Seq[SearchViewItem]()

        contactsSection = contactsSection ++ localResults.indices.map { i =>
          new ConnectionViewItem(ConnectionViewModel(i, localResults(i).id.str.hashCode, isConnected = true, localResults, localResults(i).displayName))
        }

        val shouldCollapse = searchController.filter.currentValue.exists(_.nonEmpty) && collapsedContacts && contactsSection.size > CollapsedContacts

        contactsSection = contactsSection.sortBy(_.name.str).take(if (shouldCollapse) CollapsedContacts else contactsSection.size)

        mergedResult = mergedResult ++ contactsSection
        if (shouldCollapse) {
          val expandViewItem = new ExpandViewItem(ExpandViewModel(ContactsSection, 0, localResults.size))
          mergedResult = mergedResult ++ Seq(expandViewItem)
        }
      }
    }

    def addGroupConversations(): Unit = {
      if (conversations.nonEmpty) {
        val groupConversationSectionHeader = new SectionViewItem(SectionViewModel(GroupConversationsSection, 0, teamName))
        mergedResult = mergedResult ++ Seq(groupConversationSectionHeader)

        val shouldCollapse = collapsedGroups && conversations.size > CollapsedGroups

        mergedResult = mergedResult ++ conversations.indices.map { i =>
          new GroupConversationViewItem(GroupConversationViewModel(i, conversations(i).id.str.hashCode, conversations))
        }.take(if (shouldCollapse) CollapsedGroups else conversations.size)
        if (shouldCollapse) {
          val expandViewItem = new ExpandViewItem(ExpandViewModel(GroupConversationsSection, 0, conversations.size))
          mergedResult = mergedResult ++ Seq(expandViewItem)
        }
      }
    }

    def addConnections(): Unit = {
      if (directoryResults.nonEmpty) {
        val directorySectionHeader = new SectionViewItem(SectionViewModel(DirectorySection, 0))
        mergedResult = mergedResult ++ Seq(directorySectionHeader)
        mergedResult = mergedResult ++ directoryResults.indices.map { i =>
          new ConnectionViewItem(ConnectionViewModel(i, directoryResults(i).id.str.hashCode, isConnected = false, directoryResults))
        }
      }
    }

    def addIntegrations(): Unit = {
      if (integrations.nonEmpty) {
        mergedResult = mergedResult ++ integrations.indices.map { i =>
          new IntegrationViewItem(IntegrationViewModel(i, integrations(i).id.str.hashCode, integrations))
        }
      }
    }

    def addGroupCreationButton(): Unit =
      mergedResult = mergedResult ++ Seq(new TopUserButtonViewItem(TopUserButtonViewModel(NewConversation, TopUsersSection, 0)))

    def addGuestRoomCreationButton(): Unit =
      mergedResult = mergedResult ++ Seq(new TopUserButtonViewItem(TopUserButtonViewModel(NewGuestRoom, TopUsersSection, 0)))

    def addManageServicesButton(): Unit =
      mergedResult = mergedResult ++ Seq(new TopUserButtonViewItem(TopUserButtonViewModel(ManageServices, TopUsersSection, 0)))

    if (team.isDefined) {
      if (searchController.tab.currentValue.contains(Tab.Services)) {
        if (currentUserIsAdmin && !noServices) addManageServicesButton()
        addIntegrations()
      } else {
        if (searchController.filter.currentValue.forall(_.isEmpty) && !userAccountsController.isPartner.currentValue.get) {
          addGroupCreationButton()
          addGuestRoomCreationButton()
        }
        addContacts()
        addGroupConversations()
        addConnections()
      }
    } else {
      if (searchController.filter.currentValue.forall(_.isEmpty) && !userAccountsController.isPartner.currentValue.get)
        addGroupCreationButton()
      addTopPeople()
      addContacts()
      addGroupConversations()
      addConnections()
    }
    resultsLiveData.setValue(mergedResult)
  }
}
