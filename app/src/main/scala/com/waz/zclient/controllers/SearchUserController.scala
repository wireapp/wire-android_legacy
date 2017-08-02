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
package com.waz.zclient.controllers

import com.waz.api.{Contact, Contacts, UpdateListener}
import com.waz.model._
import com.waz.service.{SearchResults, SearchState, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.Locales
import com.waz.utils.events.{EventContext, EventStream, RefreshingSignal, Signal}
import com.waz.zclient.utils.{ConversationMembersSignal, UiStorage}
import com.waz.zclient.{Injectable, Injector}

import scala.collection.immutable.Set
import scala.concurrent.duration._
import scala.language.postfixOps

class SearchUserController(initialState: SearchState)(implicit injector: Injector, ec: EventContext) extends Injectable {
  implicit private val uiStorage = inject[UiStorage]

  private val zms = inject[Signal[ZMessaging]]

  val searchState = Signal(initialState)
  private val throttledSearchState = searchState.throttle(500 millis)

  var selectedUsers = Set[UserId]()

  var excludedUsers = for {
    z           <- zms
    searchState <- throttledSearchState
    members     <- searchState.addingToConversation.fold(Signal.const(Set[UserId]()))(ConversationMembersSignal(_))
  } yield members.filterNot(_ == z.selfUserId)

  val onSelectedUserAdded = EventStream[UserId]()
  val onSelectedUserRemoved = EventStream[UserId]()
  val selectedUsersSignal = new RefreshingSignal[Set[UserId], UserId](CancellableFuture.successful(selectedUsers), EventStream.union(onSelectedUserAdded, onSelectedUserRemoved))

  EventStream.union(onSelectedUserAdded, onSelectedUserRemoved).on(Threading.Ui) { _ => setHasSelectedUsers(selectedUsers.nonEmpty) }

  def addUser(userId: UserId): Unit = {
    selectedUsers = selectedUsers ++ Set(userId)
    onSelectedUserAdded ! userId
  }

  def removeUser(userId: UserId): Unit = {
    selectedUsers = selectedUsers -- Set(userId)
    onSelectedUserRemoved ! userId
  }

  def setFilter(filter: String): Unit = searchState.mutate(_.copy(filter = filter))

  private def setHasSelectedUsers(hasSelectedUsers: Boolean) = searchState.mutate(_.copy(hasSelectedUsers = hasSelectedUsers))

  //TODO: remove this old api....
  val contactsSignal = Signal[Seq[Contact]]()
  var uiContacts: Option[Contacts] = None
  throttledSearchState.zip(zms.map(_.teamId)).on(Threading.Ui) {
    case (SearchState(filter , false, None), None) => uiContacts.foreach(_.search(filter))
    case _ =>
  }

  private val contactsUpdateListener: UpdateListener = new UpdateListener() {
    def updated(): Unit = uiContacts.foreach(contacts =>  contactsSignal ! (0 until contacts.size()).map(contacts.get).filter(c => c.getUser == null))
  }

  def setContacts(contacts: Contacts): Unit = {
    uiContacts.foreach(_.removeUpdateListener(contactsUpdateListener))
    uiContacts = Some(contacts)
    uiContacts.foreach(_.addUpdateListener(contactsUpdateListener))
    contactsUpdateListener.updated()
  }

  val allDataSignal = for {
    z                                     <- zms
    searchState                           <- throttledSearchState
    excludedUsers                         <- excludedUsers
    SearchResults(top, local, convs, dir) <- z.userSearch.search(searchState, excludedUsers)
    contacts                              <- if (searchState.shouldShowAbContacts(z.teamId.isDefined))
                                              contactsSignal.orElse(Signal.const(Seq.empty[Contact]))
                                             else Signal(Seq.empty[Contact])
  } yield (top, local.map(lr => moveToFront(lr, searchState)), convs, contacts, dir.map(dr => moveToFront(dr, searchState)))

  private def moveToFront(results: IndexedSeq[UserData], searchState: SearchState): IndexedSeq[UserData] = {
    val predicate: (UserData) => Int =
      if (searchState.isHandle)
        (u: UserData) => if (u.handle.exists(_.exactMatchQuery(searchState.filter))) 0 else 1
      else (u: UserData) => {
        val userName = toLower(u.getDisplayName)
        val query = toLower(searchState.stripSymbol)
        if (userName == query) 0 else if (userName.startsWith(query)) 1 else 2
      }

    results.sortBy(predicate)
  }

  private def toLower(str: String) = Locales.transliteration.transliterate(str).trim.toLowerCase

}
