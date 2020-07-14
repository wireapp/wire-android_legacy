package com.waz.zclient.storage.db.receipts

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ReadReceiptsDao {
    @Query("SELECT * FROM ReadReceipts")
    suspend fun allReceipts(): List<ReadReceiptsEntity>

    @Query("SELECT * FROM ReadReceipts ORDER BY message_id, user_id LIMIT :batchSize OFFSET :offset")
    suspend fun getReadReceiptsInBatch(batchSize: Int, offset: Int): List<ReadReceiptsEntity>
}
