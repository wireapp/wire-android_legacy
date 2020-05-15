package com.waz.zclient.storage.db.receipts

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ReadReceiptsDao {
    @Query("SELECT * FROM ReadReceipts")
    suspend fun allReceipts(): List<ReadReceiptsEntity>
}
