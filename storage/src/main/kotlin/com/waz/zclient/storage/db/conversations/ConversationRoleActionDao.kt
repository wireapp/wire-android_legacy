package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationRoleActionDao {

    @Query("SELECT * FROM ConversationRoleAction")
    suspend fun allConversationRoleActions(): List<ConversationRoleActionEntity>

    @Insert
    suspend fun insertConversationRoleAction(roleAction: ConversationRoleActionEntity)

    @Query("SELECT * FROM ConversationRoleAction ORDER BY label, conv_id LIMIT :batchSize OFFSET :offset")
    suspend fun getConversationRoleActionsInBatch(batchSize: Int, offset: Int): List<ConversationRoleActionEntity>
}
