package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity

class ConversationMemberLocalDataSource(private val conversationMembersDao: ConversationMembersDao) {
    suspend fun getAllConversationMembers(): List<ConversationMembersEntity> = conversationMembersDao.allConversationMembers()
}