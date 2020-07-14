package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationsDao {

    @Query("SELECT * FROM Conversations")
    suspend fun allConversations(): List<ConversationsEntity>

    @Insert
    suspend fun insertConversation(conversation: ConversationsEntity)

    @Query("SELECT * FROM Conversations ORDER BY _id LIMIT :batchSize OFFSET :offset")
    suspend fun getConversationsInBatch(batchSize: Int, offset: Int): List<ConversationsEntity>
}
