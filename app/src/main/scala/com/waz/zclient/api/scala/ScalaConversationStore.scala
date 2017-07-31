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

import android.os.Handler
import com.waz.api._
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.core.stores.conversation.{ConversationChangeRequester, ConversationStoreObserver, IConversationStore, OnConversationLoadedListener}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._

class ScalaConversationStore(zMessagingApi: ZMessagingApi, selectionController: => SelectionController) extends IConversationStore with SelectionController.ConversationChangedListener {
  selectionController.setOnConversationChangeCallback(this)

  // observers attached to a IConversationStore
  private var conversationStoreObservers = Set.empty[ConversationStoreObserver]

  private val conversationsList = zMessagingApi.getConversations
  private val establishedConversationsList = conversationsList.getEstablishedConversations

  private val syncIndicator = conversationsList.getSyncIndicator

  private var menuConversation: Option[IConversation] = None

  private val syncStateUpdateListener = new UpdateListener() {
    override def updated(): Unit = notifySyncChanged(syncIndicator.getState)
  }
  syncIndicator.addUpdateListener(syncStateUpdateListener)

  private val menuConversationUpdateListener = new UpdateListener() {
    override def updated() = notifyMenuConversationUpdated()
  }

  override def currentConversation: Option[IConversation] = selectionController.selectedConversation

  private val conversationListUpdateListener = new UpdateListener() {
    override def updated(): Unit = {
      if (conversationsList.size() == 0 && conversationsList.isReady) {
        conversationsList.setSelectedConversation(null)
      }

      notifyConversationListUpdated()
    }
  }
  conversationsList.addUpdateListener(conversationListUpdateListener)
  conversationListUpdateListener.updated()


  override def tearDown(): Unit = {
    syncIndicator.removeUpdateListener(syncStateUpdateListener)
    conversationsList.removeUpdateListener(conversationListUpdateListener)
    conversationsList.setSelectedConversation(null)

    menuConversation.foreach(menuConv => menuConv.removeUpdateListener(menuConversationUpdateListener))
    menuConversation = None
  }

  override def onLogout(): Unit = conversationsList.setSelectedConversation(null)

  override def getConversation(conversationId: String): IConversation = conversationsList.getConversation(conversationId)

  override def loadConversation(conversationId: String, onConversationLoadedListener: OnConversationLoadedListener): Unit = {
    conversationsList.getConversation(conversationId, new ConversationsList.ConversationCallback() {
      override def onConversationsFound(conversations: java.lang.Iterable[IConversation]): Unit = {
        onConversationLoadedListener.onConversationLoaded(conversations.iterator().next())
      }
    })
  }

  override def setCurrentConversation(conversation: Option[IConversation], conversationChangerSender: ConversationChangeRequester): Unit = {
    conversation.foreach { conv =>
      conv.setArchived(false)
      info(s"Set current conversation to ${conv.getName}, requester $conversationChangerSender")
    }

    if(conversation.isEmpty) info(s"Set current conversation to null, requester $conversationChangerSender")

    val oldConversation = if (conversationChangerSender == ConversationChangeRequester.FIRST_LOAD) None else currentConversation
    conversationsList.setSelectedConversation(conversation.getOrElse(null))

    if (oldConversation.map(_.getId) != conversation.map(_.getId)) {
      // Notify explicitly if the conversation doesn't change, the UiSignal notifies only when the conversation changes
      notifyCurrentConversationHasChanged(oldConversation, conversation, conversationChangerSender)
    }
  }

  override def loadCurrentConversation(onConversationLoadedListener: OnConversationLoadedListener): Unit =
    currentConversation.foreach(onConversationLoadedListener.onConversationLoaded)

  override def setCurrentConversationToNext(requester: ConversationChangeRequester): Unit = setCurrentConversation(nextConversation, requester)

  override def nextConversation: Option[IConversation] = if (conversationsList.size() == 0) None else
    (0 until conversationsList.size()).find(i => currentConversation.contains(conversationsList.get(i))).flatMap { i =>
      Some(if (i == conversationsList.size() - 1) conversationsList.get(i - 1) else conversationsList.get(i + 1))
    }

  override def loadMenuConversation(conversationId: String): Unit = {
    menuConversation = Option(conversationsList.getConversation(conversationId))
    menuConversation.foreach { conv =>
      conv.removeUpdateListener(menuConversationUpdateListener)
      conv.addUpdateListener(menuConversationUpdateListener)
      menuConversationUpdateListener.updated()
    }
  }

  override def numberOfActiveConversations: Int = if (establishedConversationsList == null) 0 else establishedConversationsList.size

  override def conversationSyncingState: SyncState = syncIndicator.getState

  override def addConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {
    // Prevent concurrent modification (if this add was executed by one of current observers during notify* callback)
    conversationStoreObservers = conversationStoreObservers + conversationStoreObserver
  }

  override def addConversationStoreObserverAndUpdate(conversationStoreObserver: ConversationStoreObserver): Unit = {
    debug(s"addConversationStoreObserverAndUpdate, current conv: $currentConversation")
    addConversationStoreObserver(conversationStoreObserver)
    currentConversation.foreach { conv =>
      conversationStoreObserver.onCurrentConversationHasChanged(null, conv, ConversationChangeRequester.UPDATER)
      conversationStoreObserver.onConversationSyncingStateHasChanged(conversationSyncingState)
    }
    conversationStoreObserver.onConversationListUpdated(conversationsList)
  }

  override def removeConversationStoreObserver(conversationStoreObserver: ConversationStoreObserver): Unit = {
    // Prevent concurrent modification
    conversationStoreObservers = conversationStoreObservers - conversationStoreObserver
  }

  override def createGroupConversation(users: Seq[User], conversationChangerSender: ConversationChangeRequester): Unit = {
    conversationsList.createGroupConversation(users, new ConversationsList.ConversationCallback() {
      override def onConversationsFound(iterable: java.lang.Iterable[IConversation]): Unit = {
        val iterator = iterable.iterator()
        if (iterator.hasNext) setCurrentConversation(
          Option(iterator.next()),
          if (conversationChangerSender != ConversationChangeRequester.START_CONVERSATION_FOR_CALL &&
              conversationChangerSender != ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL &&
              conversationChangerSender != ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA) ConversationChangeRequester.START_CONVERSATION
          else conversationChangerSender
        )
      }
    })
  }

  override def sendMessage(message: String): Unit = sendMessage(currentConversation, message)

  override def sendMessage(conversation: Option[IConversation], message: String): Unit = conversation.foreach {
    _.sendMessage(new MessageContent.Text(message))
  }

  override def sendMessage(jpegData: Array[Byte]): Unit = currentConversation.foreach {
    _.sendMessage(new MessageContent.Image(ImageAssetFactory.getImageAsset(jpegData)))
  }

  override def sendMessage(imageAsset: ImageAsset): Unit = sendMessage(currentConversation, imageAsset)

  override def sendMessage(location: MessageContent.Location): Unit = currentConversation.foreach {
    _.sendMessage(location)
  }

  override def sendMessage(assetForUpload: AssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit =
    sendMessage(currentConversation, assetForUpload, errorHandler)

  override def sendMessage(conversation: Option[IConversation], assetForUpload: AssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit =
    conversation.foreach { conv =>
      info(s"Send file to ${conv.getName}")
      conv.sendMessage(new MessageContent.Asset(assetForUpload, errorHandler))
    }

  override def sendMessage(conversation: Option[IConversation], imageAsset: ImageAsset): Unit = conversation.foreach {
    _.sendMessage(new MessageContent.Image(imageAsset))
  }

  override def sendMessage(audioAssetForUpload: AudioAssetForUpload, errorHandler: MessageContent.Asset.ErrorHandler): Unit =
    sendMessage(currentConversation, audioAssetForUpload, errorHandler)

  override def sendMessage(conversation: Option[IConversation],
                           audioAssetForUpload: AudioAssetForUpload,
                           errorHandler: MessageContent.Asset.ErrorHandler): Unit = conversation.foreach { conv =>
    info(s"Send audio file to ${conv.getName}")
    conv.sendMessage(new MessageContent.Asset(audioAssetForUpload, errorHandler))
  }

  override def knockCurrentConversation() = currentConversation.foreach( _.knock() )

  override def mute(): Unit = currentConversation.foreach(conv => mute(conv, !conv.isMuted))

  override def mute(conversation: IConversation, mute: Boolean): Unit = conversation.setMuted(mute)

  override def archive(conversation: IConversation, archive: Boolean): Unit = if (conversation.isSelected) {
    nextConversation.foreach { conv =>
      // don't want to change selected item immediately
      new Handler().postDelayed(new Runnable() {
        override def run(): Unit = setCurrentConversation(Some(conv), ConversationChangeRequester.ARCHIVED_RESULT)
      }, ScalaConversationStore.ARCHIVE_DELAY)
    }

    conversation.setArchived(archive)

    // Set current conversation to unarchived
    if (!archive) setCurrentConversation(Some(conversation), ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION)
  }

  override def leave(conversation: IConversation): Unit = conversation.leave()

  override def deleteConversation(conversation: IConversation, leaveConversation: Boolean): Unit =
    if (leaveConversation) conversation.leave()
    else conversation.clear()

  private def notifyConversationListUpdated() = conversationStoreObservers.foreach(_.onConversationListUpdated(conversationsList))

  private def notifyMenuConversationUpdated() = menuConversation.foreach { conv =>
    conversationStoreObservers.foreach(_.onMenuConversationHasChanged(conv))
  }

  private def notifyCurrentConversationHasChanged(from: Option[IConversation],
                                                  to: Option[IConversation],
                                                  sender: ConversationChangeRequester) = conversationStoreObservers.foreach {
    _.onCurrentConversationHasChanged(from.getOrElse(null), to.getOrElse(null), sender)
  }

  private def notifySyncChanged(syncState: SyncState) = conversationStoreObservers.foreach {
    _.onConversationSyncingStateHasChanged(syncState)
  }

  override def onConversationChanged(prev: Option[IConversation], current: Option[IConversation]): Unit =
    notifyCurrentConversationHasChanged(prev, current, ConversationChangeRequester.UPDATER)
}

object ScalaConversationStore {
  val TAG = ScalaConversationStore.this.getClass.getName
  val ARCHIVE_DELAY = 500
}
