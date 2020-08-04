package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.feature.backup.io.database.SingleReadDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity

class ConversationRolesBackupDao(private val conversationRoleActionDao: ConversationRoleActionDao) :
    SingleReadDao<ConversationRoleActionEntity> {

    override suspend fun insert(item: ConversationRoleActionEntity) =
        conversationRoleActionDao.insert(item)

    override suspend fun allItems(): List<ConversationRoleActionEntity> =
        conversationRoleActionDao.allConversationRoleActions()
}
