package com.waz.zclient.storage.db.history

import androidx.room.Dao
import androidx.room.Query

@Dao
interface EditHistoryDao {
    @Query("SELECT * FROM EditHistory")
    suspend fun allHistory(): List<EditHistoryEntity>
}
