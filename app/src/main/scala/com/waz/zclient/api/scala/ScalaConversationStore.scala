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
package com.waz.zclient.api.scala

import com.waz.api._
import com.waz.zclient.core.stores.conversation.{ConversationStoreObserver, IConversationStore}
import com.waz.model.ConvId

class ScalaConversationStore(zMessagingApi: ZMessagingApi) extends IConversationStore  {

  // observers attached to a IConversationStore
  private var conversationStoreObservers = Set.empty[ConversationStoreObserver]

  private val conversationsList = zMessagingApi.getConversations
  private val establishedConversationsList = conversationsList.getEstablishedConversations

  private val syncIndicator = conversationsList.getSyncIndicator

  private val syncStateUpdateListener = new UpdateListener() {
    override def updated(): Unit = notifySyncChanged(syncIndicator.getState)
  }
  syncIndicator.addUpdateListener(syncStateUpdateListener)

  override def getConversation(conversationId: String): IConversation = conversationsList.getConversation(conversationId)

  override def nextConversation(convId: ConvId): Option[ConvId] =
    if (conversationsList.size() <= 1) None else
      (0 until conversationsList.size()).find(i => conversationsList.get(i).getId == convId.str)
      .map { i => if (i == conversationsList.size() - 1)  conversationsList.get(i - 1) else  conversationsList.get(i + 1)}
      .map(ic => new ConvId(ic.getId))

  override def numberOfActiveConversations: Int = if (establishedConversationsList == null) 0 else establishedConversationsList.size

  override protected def conversationSyncingState: SyncState = syncIndicator.getState

  override def addConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {
    // Prevent concurrent modification (if this add was executed by one of current observers during notify* callback)
    conversationStoreObservers = conversationStoreObservers + conversationStoreObserver
  }

  override def addConversationStoreObserverAndUpdate(conversationStoreObserver: ConversationStoreObserver): Unit = {
      //debug(s"addConversationStoreObserverAndUpdate, current conv: $currentConversation")
    addConversationStoreObserver(conversationStoreObserver)

    conversationStoreObserver.onConversationSyncingStateHasChanged(conversationSyncingState)

    conversationStoreObserver.onConversationListUpdated(conversationsList)
  }

  override def removeConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {
    // Prevent concurrent modification
    conversationStoreObservers = conversationStoreObservers - conversationStoreObserver
  }

  private def notifyConversationListUpdated() = conversationStoreObservers.foreach(_.onConversationListUpdated(conversationsList))

  private def notifySyncChanged(syncState: SyncState) = conversationStoreObservers.foreach {
    _.onConversationSyncingStateHasChanged(syncState)
  }

  override def tearDown(): Unit = {}
}
