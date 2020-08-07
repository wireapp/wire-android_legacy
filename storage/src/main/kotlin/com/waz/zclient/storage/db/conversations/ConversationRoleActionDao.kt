package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ConversationRoleActionDao : BatchDao<ConversationRoleActionEntity> {

    @Query("SELECT * FROM ConversationRoleAction")
    suspend fun allConversationRoleActions(): List<ConversationRoleActionEntity>

    @Query("SELECT * FROM ConversationRoleAction ORDER BY conv_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ConversationRoleActionEntity>?

    @Query("SELECT COUNT(*) FROM ConversationRoleAction")
    override suspend fun count(): Int
}
