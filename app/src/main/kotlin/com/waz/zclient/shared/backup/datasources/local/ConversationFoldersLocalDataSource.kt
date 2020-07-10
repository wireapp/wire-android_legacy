package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import kotlinx.serialization.Serializable

class ConversationFoldersLocalDataSource(private val conversationFoldersDao: ConversationFoldersDao) {
    suspend fun getAllConversationFolders(): List<ConversationFoldersEntity> = conversationFoldersDao.allConversationFolders()
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
        fun from(entity: ConversationFoldersEntity): ConversationFoldersEntity = ConversationFoldersEntity(
            convId = entity.convId,
            folderId = entity.folderId
        )
    }
}