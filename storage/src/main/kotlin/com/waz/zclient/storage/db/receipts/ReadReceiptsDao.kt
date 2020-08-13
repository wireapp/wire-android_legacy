package com.waz.zclient.storage.db.receipts

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ReadReceiptsDao : BatchDao<ReadReceiptsEntity> {
    @Query("SELECT * FROM ReadReceipts")
    suspend fun allReceipts(): List<ReadReceiptsEntity>

    @Query("SELECT * FROM ReadReceipts ORDER BY message_id, user_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ReadReceiptsEntity>?

    @Query("SELECT COUNT(*) FROM ReadReceipts")
    override suspend fun count(): Int
}
