/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.core.stores.conversation

import com.waz.api.AssetForUpload
import com.waz.api.AudioAssetForUpload
import com.waz.api.IConversation
import com.waz.api.ImageAsset
import com.waz.api.MessageContent
import com.waz.api.SyncState
import com.waz.api.User
import com.waz.zclient.core.stores.IStore

import collection.JavaConverters._

trait IConversationStore extends IStore {

  /**
   * adds an observer on this store
   * @param conversationStoreObserver
   */
  def addConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit

  def addConversationStoreObserverAndUpdate(conversationStoreObserver: ConversationStoreObserver): Unit

  /**
   * removes an observer on this store
   * @param conversationStoreObserver
   */
  def removeConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit

  def mute(): Unit

  /**
   * mute conversation
   * @param conversation
   * @param mute
   */
  def mute(conversation: IConversation, mute: Boolean): Unit

  /**
   * archive conversation
   * @param conversation
   * @param archive
   */
  def archive(conversation: IConversation, archive: Boolean): Unit

  /**
   * Leaves conversation
   * @param conversation
   */
  def leave(conversation: IConversation): Unit

  /**
   * Deletes conversation
   * @param conversation
   */
  def deleteConversation(conversation: IConversation, leaveConversation: Boolean): Unit


  /**
   * gets the current conversation
   * @return
   */
  def getCurrentConversation(): IConversation = currentConversation.getOrElse(null) // for Java
  def currentConversation: Option[IConversation] // for Scala

  /**
   * sets the current conversation so that the message fragment gets informed
    * @param conversation
   * @param conversationChangerSender
   */
  def setCurrentConversation(conversation: Option[IConversation], conversationChangerSender: ConversationChangeRequester): Unit // for Scala
  def setCurrentConversation(conversation: IConversation, conversationChangerSender: ConversationChangeRequester): Unit = // for Java
    setCurrentConversation(Option(conversation), conversationChangerSender)
  /**
   * Same as calling {@code setCurrentConversation(getNextConversation())}
   * @param requester
   */
  def setCurrentConversationToNext(requester: ConversationChangeRequester): Unit

  /**
   * For use when archiving a conversation - you need to set a new current conversation
   *
   * @return IConversation - if the below conversation is not archived this will be returned,
   * otherwise the conversation above
   */
  def getNextConversation(): IConversation = nextConversation.getOrElse(null) // for Java
  def nextConversation: Option[IConversation] // for Scala

  def getConversation(conversationId: String): IConversation

  def sendMessage(message: String): Unit

  def sendMessage(conversation: Option[IConversation], message: String): Unit

  def sendMessage(jpegData: Array[Byte]): Unit

  def sendMessage(imageAsset: ImageAsset): Unit

  def sendMessage(location: MessageContent.Location): Unit

  def sendMessage(assetForUpload: AssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit

  def sendMessage(conversation: Option[IConversation], assetForUpload: AssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit

  def sendMessage(conversation: Option[IConversation], imageAsset: ImageAsset): Unit // for Scala
  def sendMessage(conversation: IConversation, imageAsset: ImageAsset): Unit = sendMessage(Option(conversation), imageAsset) // for Java

  def sendMessage(audioAssetForUpload: AudioAssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit

  def sendMessage(conversation: Option[IConversation], audioAssetForUpload: AudioAssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit

  def knockCurrentConversation(): Unit

  def createGroupConversation(users: Seq[User], conversationChangerSender: ConversationChangeRequester): Unit
  def createGroupConversation(users: java.lang.Iterable[_ <: User], conversationChangerSender: ConversationChangeRequester): Unit =
    createGroupConversation(users.asScala.toSeq, conversationChangerSender)

  def loadCurrentConversation(onConversationLoadedListener: OnConversationLoadedListener): Unit

  def loadConversation(conversationId: String, onConversationLoadedListener: OnConversationLoadedListener): Unit

  def loadMenuConversation(conversationId: String): Unit

  def numberOfActiveConversations: Int

  def conversationSyncingState: SyncState

  def onLogout()
}
