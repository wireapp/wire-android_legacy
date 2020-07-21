package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import kotlinx.serialization.Serializable

class ConversationMembersLocalDataSource(dao: ConversationMembersDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ConversationMembersEntity, ConversationMembersJSONEntity>
    ("conversationMembers", dao, batchSize, ConversationMembersJSONEntity.serializer()) {
    override fun toJSON(entity: ConversationMembersEntity) = ConversationMembersJSONEntity.from(entity)
    override fun toEntity(json: ConversationMembersJSONEntity) = json.toEntity()
}

@Serializable
data class ConversationMembersJSONEntity(val userId: String = "", val conversationId: String = "", val role: String = "") {
    fun toEntity() = ConversationMembersEntity(
        userId = userId,
        conversationId = conversationId,
        role = role
    )

    companion object {
        fun from(entity: ConversationMembersEntity) = ConversationMembersJSONEntity(
            userId = entity.userId,
            conversationId = entity.conversationId,
            role = entity.role
        )
    }
}
