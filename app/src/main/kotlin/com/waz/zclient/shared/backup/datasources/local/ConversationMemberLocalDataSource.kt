package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import kotlinx.serialization.Serializable

class ConversationMemberLocalDataSource(private val conversationMembersDao: ConversationMembersDao): BackupLocalDataSource<ConversationMembersEntity>() {
    override suspend fun getAll(): List<ConversationMembersEntity> = conversationMembersDao.allConversationMembers()

    override fun serialize(entity: ConversationMembersEntity): String =
        json.stringify(ConversationMembersJSONEntity.serializer(), ConversationMembersJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): ConversationMembersEntity =
        json.parse(ConversationMembersJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class ConversationMembersJSONEntity(
    val userId: String = "",
    val conversationId: String = "",
    val role: String = ""
) {
    fun toEntity(): ConversationMembersEntity = ConversationMembersEntity(
        userId = userId,
        conversationId = conversationId,
        role = role
    )

    companion object {
        fun from(entity: ConversationMembersEntity): ConversationMembersJSONEntity = ConversationMembersJSONEntity(
            userId = entity.userId,
            conversationId = entity.conversationId,
            role = entity.role
        )
    }
}
