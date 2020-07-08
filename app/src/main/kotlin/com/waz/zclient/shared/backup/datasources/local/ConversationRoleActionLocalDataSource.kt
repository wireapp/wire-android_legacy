package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity

class ConversationRoleActionLocalDataSource(private val conversationRoleActionDao: ConversationRoleActionDao) {
    suspend fun getAllConversationRoleActions(): List<ConversationRoleActionEntity> = conversationRoleActionDao.allConversationRoleActions()
}