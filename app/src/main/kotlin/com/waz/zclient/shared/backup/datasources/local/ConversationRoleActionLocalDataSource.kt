package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import kotlinx.serialization.Serializable

class ConversationRoleActionLocalDataSource(private val conversationRoleActionDao: ConversationRoleActionDao) {
    suspend fun getAllConversationRoleActions(): List<ConversationRoleActionEntity> = conversationRoleActionDao.allConversationRoleActions()
}

@Serializable
data class ConversationRoleActionJSONEntity(
    val label: String = "",
    val action: String = "",
    val convId: String = ""
) {
    fun toEntity(): ConversationRoleActionEntity = ConversationRoleActionEntity(
        label = label,
        action = action,
        convId = convId
    )

    companion object {
        fun from(entity: ConversationRoleActionEntity): ConversationRoleActionJSONEntity = ConversationRoleActionJSONEntity(
            label = entity.label,
            action = entity.action,
            convId = entity.convId
        )
    }
}