package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ConversationMembersDao : BatchReader<ConversationMembersEntity> {

    @Query("SELECT * FROM ConversationMembers")
    suspend fun allConversationMembers(): List<ConversationMembersEntity>

    @Insert
    suspend fun insertConversationMemeber(conversationMember: ConversationMembersEntity)

    @Query("SELECT * FROM ConversationMembers ORDER BY user_id, conv_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ConversationMembersEntity>?

    @Query("SELECT COUNT(*) FROM ConversationMembers")
    override suspend fun size(): Int
}
