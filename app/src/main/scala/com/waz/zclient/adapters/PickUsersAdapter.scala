/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.adapters

import android.support.v7.widget.RecyclerView
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.api.{Contact, ContactDetails, User}
import com.waz.model._
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.adapters.PickUsersAdapter._
import com.waz.zclient.controllers.{SearchUserController, UserAccountsController}
import com.waz.zclient.viewholders._
import com.waz.zclient.views.pickuser.ContactRowView.Callback

import scala.concurrent.duration._

class PickUsersAdapter(topUsersOnItemTouchListener: SearchResultOnItemTouchListener,
                       adapterCallback: PickUsersAdapter.Callback,
                       searchUserController: SearchUserController,
                       darkTheme: Boolean)
                      (implicit injector: Injector) extends RecyclerView.Adapter[RecyclerView.ViewHolder] with Injectable {

  implicit private val ec = EventContext.Implicits.global
  implicit private val logTag = ZLog.logTagFor[PickUsersAdapter]

  setHasStableIds(true)

  private val userAccountsController = inject[UserAccountsController]

  private var mergedResult = Seq[SearchResult]()
  private var collapsedContacts = true
  private var collapsedGroups = true

  private val contactsCallback = new Callback {
    override def onContactListContactClicked(contactDetails: ContactDetails) = adapterCallback.onContactListContactClicked(contactDetails)
    override def getDestination = adapterCallback.getDestination
    override def isUserSelected(user: User) = adapterCallback.getSelectedUsers.contains(UserId(user.getId))
    override def onContactListUserClicked(user: User) = adapterCallback.onContactListUserClicked(UserId(user.getId))
  }

  private var topUsers = Seq[UserData]()
  private var teamMembers = Seq[UserData]()
  private var conversations = Seq[ConversationData]()
  private var connectedUsers = Seq[UserData]()
  private var contacts = Seq[Contact]()
  private var directoryResults = Seq[UserData]()
  private var currentUser = Option.empty[UserData]

  searchUserController.allDataSignal.throttle(500.millis).on(Threading.Ui) {
    case (newTopUsers, newTeamMembers, newConversations, newConnectedUsers, newContacts, newDirectoryResults) =>
      topUsers = newTopUsers
      teamMembers = newTeamMembers
      conversations = newConversations
      connectedUsers = newConnectedUsers
      contacts = newContacts
      directoryResults = newDirectoryResults
      updateMergedResults()
  }

  userAccountsController.currentUser.on(Threading.Ui){ user =>
    currentUser = user
    updateMergedResults()
  }

  private def updateMergedResults(): Unit ={
    mergedResult = Seq()

    val teamName = currentUser.fold("")(_.getDisplayName)//TODO: Get team name from UserData

    def addTopPeople(): Unit = {
      if (topUsers.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, TopUsersSection, 0))
        mergedResult = mergedResult ++ Seq(SearchResult(TopUsers, TopUsersSection, 0))
      }
    }
    def addTeamMembers(): Unit = {
      if (teamMembers.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, TeamMembersSection, 0, teamName))
        mergedResult = mergedResult ++ teamMembers.indices.map { i =>
          SearchResult(ConnectedUser, TeamMembersSection, i, teamMembers(i).id.str.hashCode)
        }
      }
    }
    def addContacts(): Unit = {
      if (contacts.nonEmpty || connectedUsers.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, ContactsSection, 0))
        var contactsSection = Seq[SearchResult]()

        contactsSection = contactsSection ++ contacts.indices.map { i =>
          val name = Option(contacts(i).getDetails).map(_.getDisplayName).getOrElse("")
          SearchResult(AddressBookContact, ContactsSection, i, name)
        }

        contactsSection = contactsSection ++ connectedUsers.indices.map { i =>
          SearchResult(ConnectedUser, ContactsSection, i, connectedUsers(i).id.str.hashCode, connectedUsers(i).getDisplayName)
        }

        val shouldCollapse = searchUserController.searchState.currentValue.exists(_.filter.nonEmpty) && collapsedContacts && contactsSection.size > CollapsedContacts

        contactsSection = contactsSection.sortBy(_.name).take(if (shouldCollapse) CollapsedContacts else contactsSection.size)

        mergedResult = mergedResult ++ contactsSection
        if (shouldCollapse) {
          mergedResult = mergedResult ++ Seq(SearchResult(Expand, ContactsSection, 0))
        }
      }
    }
    def addGroupConversations(): Unit = {
      if (conversations.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, GroupConversationsSection, 0, teamName))

        val shouldCollapse = collapsedGroups && conversations.size > CollapsedGroups

        mergedResult = mergedResult ++ conversations.indices.map { i =>
          SearchResult(GroupConversation, GroupConversationsSection, i, conversations(i).id.str.hashCode)
        }.take(if (shouldCollapse) CollapsedGroups else conversations.size)
        if (shouldCollapse) {
          mergedResult = mergedResult ++ Seq(SearchResult(Expand, GroupConversationsSection, 0))
        }
      }
    }
    def addConnections(): Unit = {
      if (directoryResults.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, DirectorySection, 0))
        mergedResult = mergedResult ++ directoryResults.indices.map { i =>
          SearchResult(UnconnectedUser, DirectorySection, i, directoryResults(i).id.str.hashCode)
        }
      }
    }

    addTopPeople()
    addTeamMembers()
    if (true/*currentUser.exists(_.teamId.nonEmpty)*/) { //TODO: check if user is a team user
      addGroupConversations()
      addContacts()
    } else {
      addContacts()
      addGroupConversations()
    }
    addConnections()

    ZLog.debug(s"Merged contacts updated: ${mergedResult.size}")
    notifyDataSetChanged()
  }

  override def getItemCount = mergedResult.size

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = {
    val item = mergedResult(position)
    item.itemType match {
      case TopUsers =>
        holder.asInstanceOf[TopUsersViewHolder].bind(topUsers)
        holder.asInstanceOf[TopUsersViewHolder].bindOnItemTouchListener(topUsersOnItemTouchListener)
      case GroupConversation =>
        val conversation = conversations(item.index)
        holder.asInstanceOf[ConversationViewHolder].bind(conversation)
      case ConnectedUser =>
        val connectedUser =
          if (item.section == TeamMembersSection)
            teamMembers(item.index)
          else
            connectedUsers(item.index)
        val contactIsSelected = searchUserController.selectedUsers.contains(connectedUser.id)
        holder.asInstanceOf[UserViewHolder].bind(connectedUser, contactIsSelected)
      case UnconnectedUser =>
        val connectedUser = directoryResults(item.index)
        val contactIsSelected = searchUserController.selectedUsers.contains(connectedUser.id)
        holder.asInstanceOf[UserViewHolder].bind(connectedUser, contactIsSelected)
      case SectionHeader =>
        holder.asInstanceOf[SectionHeaderViewHolder].bind(item.section, item.name)
      case NameInitialSeparator =>
        holder.asInstanceOf[AddressBookSectionHeaderViewHolder].bind(item.name)
      case AddressBookContact =>
        val contact  = contacts(item.index)
        holder.asInstanceOf[AddressBookContactViewHolder].bind(contact, contactsCallback)
      case Expand =>
        val itemCount =
          if (item.section == ContactsSection)
            contacts.size + connectedUsers.size
          else
            conversations.size
        holder.asInstanceOf[SectionExpanderViewHolder].bind(itemCount, new View.OnClickListener() {
          def onClick(v: View): Unit = {
            if (item.section == ContactsSection) expandContacts() else expandGroups()
          }
        })
      case _ =>
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    viewType match {
      case TopUsers =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_top_users, parent, false)
        val topUserAdapter: TopUserAdapter = new TopUserAdapter(searchUserController.selectedUsersSignal)
        new viewholders.TopUsersViewHolder(view, topUserAdapter, parent.getContext)
      case ConnectedUser | UnconnectedUser =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_user, parent, false)
        new UserViewHolder(view, true, darkTheme)
      case GroupConversation =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_conversation, parent, false)
        new ConversationViewHolder(view, darkTheme)
      case SectionHeader =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_section_header, parent, false)
        new SectionHeaderViewHolder(view)
      case NameInitialSeparator =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_section_header, parent, false)
        new AddressBookSectionHeaderViewHolder(view, darkTheme)
      case AddressBookContact =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.contactlist_user, parent, false)
        new AddressBookContactViewHolder(view, darkTheme)
      case Expand =>
        val view = LayoutInflater.from(parent.getContext).inflate(R.layout.startui_section_expander, parent, false)
        new SectionExpanderViewHolder(view)
    }
  }

  override def getItemViewType(position: Int) = mergedResult(position).itemType

  override def getItemId(position: Int) = mergedResult(position).id

  def getSectionIndexForPosition(position: Int) = mergedResult(position).index

  private def expandContacts() = {
    collapsedContacts = false
    updateMergedResults()
  }

  private def expandGroups() = {
    collapsedGroups = false
    updateMergedResults()
  }
}

object PickUsersAdapter {

  //Item Types
  val TopUsers: Int = 0
  val NameInitialSeparator: Int = 1
  val AddressBookContact: Int = 2
  val ConnectedUser: Int = 3
  val UnconnectedUser: Int = 4
  val GroupConversation: Int = 5
  val SectionHeader: Int = 6
  val Expand: Int = 7

  //Sections
  val TopUsersSection = 0
  val TeamMembersSection = 1
  val GroupConversationsSection = 2
  val ContactsSection = 3
  val DirectorySection = 4

  //Constants
  val CollapsedContacts = 5
  val CollapsedGroups = 5

  trait Callback {
    def getSelectedUsers: Set[UserId]
    def onContactListUserClicked(userId: UserId): Unit
    def onContactListContactClicked(contactDetails: ContactDetails): Unit
    def getDestination: Int
  }

}

case class SearchResult(itemType: Int, section: Int, index: Int, id: Long, name: String)

object SearchResult{
  def apply(itemType: Int, section: Int, index: Int, id: Long): SearchResult = new SearchResult(itemType, section, index, id, "")
  def apply(itemType: Int, section: Int, index: Int, name: String): SearchResult = new SearchResult(itemType, section, index, itemType + section + index, name)
  def apply(itemType: Int, section: Int, index: Int): SearchResult = SearchResult(itemType, section, index, "")
}
