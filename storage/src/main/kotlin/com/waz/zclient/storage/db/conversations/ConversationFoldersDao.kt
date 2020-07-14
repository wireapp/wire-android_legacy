package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationFoldersDao {

    @Query("SELECT * FROM ConversationFolders")
    suspend fun allConversationFolders(): List<ConversationFoldersEntity>

    @Insert
    suspend fun insertConversationFolder(conversationFolder: ConversationFoldersEntity)

    @Query("SELECT * FROM ConversationFolders ORDER BY conv_id, folder_id LIMIT :batchSize OFFSET :offset")
    suspend fun getConversationFoldersInBatch(batchSize: Int, offset: Int): List<ConversationFoldersEntity>
}
