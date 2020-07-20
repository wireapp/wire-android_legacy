package com.waz.zclient.storage.db.receipts

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ReadReceiptsDao : BatchReader<ReadReceiptsEntity> {
    @Query("SELECT * FROM ReadReceipts")
    suspend fun allReceipts(): List<ReadReceiptsEntity>

    @Query("SELECT * FROM ReadReceipts ORDER BY message_id, user_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ReadReceiptsEntity>

    @Query("SELECT COUNT(*) FROM ReadReceipts")
    override suspend fun size(): Int
}
