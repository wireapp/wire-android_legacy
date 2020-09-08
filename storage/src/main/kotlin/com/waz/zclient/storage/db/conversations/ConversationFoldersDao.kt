package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ConversationFoldersDao : BatchDao<ConversationFoldersEntity> {

    @Query("SELECT * FROM ConversationFolders")
    suspend fun allConversationFolders(): List<ConversationFoldersEntity>

    @Query("SELECT * FROM ConversationFolders ORDER BY conv_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ConversationFoldersEntity>?

    @Query("SELECT COUNT(*) FROM ConversationFolders")
    override suspend fun count(): Int
}
