package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ConversationFoldersDao : BatchReader<ConversationFoldersEntity> {

    @Query("SELECT * FROM ConversationFolders")
    suspend fun allConversationFolders(): List<ConversationFoldersEntity>

    @Insert
    suspend fun insertConversationFolder(conversationFolder: ConversationFoldersEntity)

    @Query("SELECT * FROM ConversationFolders ORDER BY conv_id, folder_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ConversationFoldersEntity>

    @Query("SELECT COUNT(*) FROM ConversationFolders")
    override suspend fun size(): Int
}
