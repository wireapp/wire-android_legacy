package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ConversationsDao : BatchReader<ConversationsEntity> {

    @Query("SELECT * FROM Conversations")
    suspend fun allConversations(): List<ConversationsEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationsEntity)

    @Query("SELECT * FROM Conversations ORDER BY _id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ConversationsEntity>

    @Query("SELECT COUNT(*) FROM Conversations")
    override suspend fun size(): Int
}
