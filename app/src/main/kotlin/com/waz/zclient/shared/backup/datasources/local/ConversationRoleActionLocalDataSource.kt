package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import kotlinx.serialization.Serializable

class ConversationRoleActionLocalDataSource(dao: ConversationRoleActionDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ConversationRoleActionEntity, ConversationRoleActionJSONEntity>
    ("conversationRoles", dao, batchSize, ConversationRoleActionJSONEntity.serializer()) {
    override fun toJSON(entity: ConversationRoleActionEntity) = ConversationRoleActionJSONEntity.from(entity)
    override fun toEntity(json: ConversationRoleActionJSONEntity) = json.toEntity()
}

@Serializable
data class ConversationRoleActionJSONEntity(val label: String = "", val action: String = "", val convId: String = "") {
    fun toEntity() = ConversationRoleActionEntity(
        label = label,
        action = action,
        convId = convId
    )

    companion object {
        fun from(entity: ConversationRoleActionEntity) = ConversationRoleActionJSONEntity(
            label = entity.label,
            action = entity.action,
            convId = entity.convId
        )
    }
}
