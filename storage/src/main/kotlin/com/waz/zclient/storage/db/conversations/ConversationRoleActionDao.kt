package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ConversationRoleActionDao : BatchReader<ConversationRoleActionEntity> {

    @Query("SELECT * FROM ConversationRoleAction")
    suspend fun allConversationRoleActions(): List<ConversationRoleActionEntity>

    @Insert
    suspend fun insertConversationRoleAction(roleAction: ConversationRoleActionEntity)

    @Query("SELECT * FROM ConversationRoleAction ORDER BY label, conv_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ConversationRoleActionEntity>?

    @Query("SELECT COUNT(*) FROM ConversationRoleAction")
    override suspend fun size(): Int
}
