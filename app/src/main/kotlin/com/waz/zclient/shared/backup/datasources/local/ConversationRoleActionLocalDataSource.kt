package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import kotlinx.serialization.Serializable

class ConversationRoleActionLocalDataSource(
    private val conversationRoleActionDao: ConversationRoleActionDao,
    batchSize: Int = BatchSize
) : BackupLocalDataSource<ConversationRoleActionEntity, ConversationRoleActionJSONEntity>
    (ConversationRoleActionJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ConversationRoleActionEntity> =
        conversationRoleActionDao.getConversationRoleActionsInBatch(batchSize, offset)

    override fun toJSON(entity: ConversationRoleActionEntity): ConversationRoleActionJSONEntity =
            ConversationRoleActionJSONEntity.from(entity)
    override fun toEntity(json: ConversationRoleActionJSONEntity): ConversationRoleActionEntity =
            json.toEntity()
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
