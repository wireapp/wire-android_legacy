package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import kotlinx.serialization.Serializable

class ConversationFoldersLocalDataSource(dao: ConversationFoldersDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ConversationFoldersEntity, ConversationFoldersJSONEntity>
    ("conversationFolders", dao, batchSize, ConversationFoldersJSONEntity.serializer()) {
    override fun toJSON(entity: ConversationFoldersEntity) = ConversationFoldersJSONEntity.from(entity)
    override fun toEntity(json: ConversationFoldersJSONEntity) = json.toEntity()
}

@Serializable
data class ConversationFoldersJSONEntity(
    val convId: String = "",
    val folderId: String = ""
) {
    fun toEntity() = ConversationFoldersEntity(
        convId = convId,
        folderId = folderId
    )

    companion object {
        fun from(entity: ConversationFoldersEntity) = ConversationFoldersJSONEntity(
            convId = entity.convId,
            folderId = entity.folderId
        )
    }
}
