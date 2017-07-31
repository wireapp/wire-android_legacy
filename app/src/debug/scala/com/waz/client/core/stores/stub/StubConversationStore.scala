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
package com.waz.client.core.stores.stub

import com.waz.api.AssetForUpload
import com.waz.api.AudioAssetForUpload
import com.waz.api.IConversation
import com.waz.api.ImageAsset
import com.waz.api.MessageContent
import com.waz.api.MessageContent.Asset
import com.waz.api.SyncState
import com.waz.api.User
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver
import com.waz.zclient.core.stores.conversation.IConversationStore
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener

class StubConversationStore extends IConversationStore {
  override def sendMessage(audioAssetForUpload: AudioAssetForUpload, errorHandler: Asset.ErrorHandler): Unit = {}

  override def sendMessage(message: String): Unit = {}

  override def mute() : Unit = {}

  override def loadMenuConversation(conversationId: String): Unit = {}

  override def leave(conversation: IConversation): Unit = {}

  override def setCurrentConversationToNext(requester: ConversationChangeRequester): Unit = {}

  override def sendMessage(jpegData: Array[Byte]): Unit = {}

  override def sendMessage(conversation: Option[IConversation], assetForUpload: AssetForUpload, errorHandler: Asset.ErrorHandler): Unit = {}

  override def sendMessage(conversation: Option[IConversation], imageAsset: ImageAsset): Unit = {}

  override def sendMessage(conversation: Option[IConversation], audioAssetForUpload: AudioAssetForUpload, errorHandler: Asset.ErrorHandler): Unit = {}

  override def numberOfActiveConversations: Int = 0

  override def addConversationStoreObserverAndUpdate(conversationStoreObserver: ConversationStoreObserver): Unit = {}

  override def mute(conversation: IConversation, mute: Boolean): Unit = {}

  override def sendMessage(conversation: Option[IConversation], message: String): Unit = {}

  override def removeConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {}

  override def addConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {}

  override def sendMessage(assetForUpload: AssetForUpload, errorHandler: Asset.ErrorHandler): Unit = {}

  override def nextConversation: Option[IConversation] = None

  override def loadCurrentConversation(onConversationLoadedListener: OnConversationLoadedListener): Unit = {}

  override def deleteConversation(conversation: IConversation, leaveConversation: Boolean): Unit = {}

  override def createGroupConversation(users: Seq[User], conversationChangerSender: ConversationChangeRequester): Unit = {}

  override def setCurrentConversation(conversation: Option[IConversation], conversationChangerSender: ConversationChangeRequester): Unit = {}

  override def sendMessage(location: MessageContent.Location): Unit = {}

  override def tearDown(): Unit = {}

  override def archive(conversation: IConversation, archive: Boolean): Unit = {}

  override def sendMessage(imageAsset: ImageAsset): Unit = {}

  override def knockCurrentConversation(): Unit = {}

  override def conversationSyncingState: SyncState = null

  override def getConversation(conversationId: String): IConversation = null

  override def onLogout(): Unit = {}

  override def currentConversation: Option[IConversation] = None

  override def loadConversation(conversationId: String, onConversationLoadedListener: OnConversationLoadedListener): Unit = {}
}
