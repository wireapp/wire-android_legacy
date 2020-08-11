package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ConversationsDao : BatchDao<ConversationsEntity> {

    @Query("SELECT * FROM Conversations")
    suspend fun allConversations(): List<ConversationsEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationsEntity)

    @Query("SELECT * FROM Conversations ORDER BY _id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ConversationsEntity>?

    @Query("SELECT COUNT(*) FROM Conversations")
    override suspend fun count(): Int
}
