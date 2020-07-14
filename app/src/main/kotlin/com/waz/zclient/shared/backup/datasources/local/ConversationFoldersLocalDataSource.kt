package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import kotlinx.serialization.Serializable

class ConversationFoldersLocalDataSource(private val conversationFoldersDao: ConversationFoldersDao): BackupLocalDataSource<ConversationFoldersEntity>() {
    override suspend fun getAll(): List<ConversationFoldersEntity> = conversationFoldersDao.allConversationFolders()
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ConversationFoldersEntity> =
        conversationFoldersDao.getConversationFoldersInBatch(batchSize, offset)

    override fun serialize(entity: ConversationFoldersEntity): String =
        json.stringify(ConversationFoldersJSONEntity.serializer(), ConversationFoldersJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): ConversationFoldersEntity =
        json.parse(ConversationFoldersJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class ConversationFoldersJSONEntity(
    val convId: String = "",
    val folderId: String = ""
) {
    fun toEntity(): ConversationFoldersEntity = ConversationFoldersEntity(
        convId = convId,
        folderId = folderId
    )

    companion object {
        fun from(entity: ConversationFoldersEntity): ConversationFoldersJSONEntity = ConversationFoldersJSONEntity(
            convId = entity.convId,
            folderId = entity.folderId
        )
    }
}
