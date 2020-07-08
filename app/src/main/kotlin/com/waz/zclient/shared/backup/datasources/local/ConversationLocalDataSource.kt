package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity

class ConversationLocalDataSource(private val conversationsDao: ConversationsDao){
    suspend fun getAllConversations(): List<ConversationsEntity> = conversationsDao.allConversations()
}