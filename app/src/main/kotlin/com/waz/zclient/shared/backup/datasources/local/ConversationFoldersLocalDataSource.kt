package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity

class ConversationFoldersLocalDataSource(private val conversationFoldersDao: ConversationFoldersDao) {
    suspend fun getAllConversationFolders(): List<ConversationFoldersEntity> = conversationFoldersDao.allConversationFolders()
}