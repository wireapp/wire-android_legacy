package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import kotlinx.serialization.Serializable

class ConversationMembersLocalDataSource(
    private val conversationMembersDao: ConversationMembersDao,
    batchSize: Int = BatchSize
) : BackupLocalDataSource<ConversationMembersEntity, ConversationMembersJSONEntity>(ConversationMembersJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ConversationMembersEntity> =
        conversationMembersDao.getConversationMembersInBatch(batchSize, offset)

    override fun toJSON(entity: ConversationMembersEntity): ConversationMembersJSONEntity = ConversationMembersJSONEntity.from(entity)
    override fun toEntity(json: ConversationMembersJSONEntity): ConversationMembersEntity = json.toEntity()
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
