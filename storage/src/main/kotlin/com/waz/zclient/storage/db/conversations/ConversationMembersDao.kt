package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationMembersDao {

    @Query("SELECT * FROM ConversationMembers")
    suspend fun allConversationMembers(): List<ConversationMembersEntity>

    @Insert
    suspend fun insertConversationMemeber(conversationMember: ConversationMembersEntity)

    @Query("SELECT * FROM ConversationMembers ORDER BY user_id, conv_id LIMIT :batchSize OFFSET :offset")
    suspend fun getConversationMembersInBatch(batchSize: Int, offset: Int): List<ConversationMembersEntity>
}
