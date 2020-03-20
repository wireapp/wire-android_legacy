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
}
