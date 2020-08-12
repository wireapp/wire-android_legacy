package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ConversationMembersDao : BatchDao<ConversationMembersEntity> {

    @Query("SELECT * FROM ConversationMembers")
    suspend fun allConversationMembers(): List<ConversationMembersEntity>

    @Insert
    suspend fun insertConversationMemeber(conversationMember: ConversationMembersEntity)

    @Query("SELECT * FROM ConversationMembers ORDER BY user_id, conv_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ConversationMembersEntity>?

    @Query("SELECT COUNT(*) FROM ConversationMembers")
    override suspend fun count(): Int
}
