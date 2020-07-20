package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface MessagesDao : BatchReader<MessagesEntity> {
    @Query("SELECT * FROM Messages")
    suspend fun allMessages(): List<MessagesEntity>

    @Query("SELECT * FROM Messages ORDER BY _id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<MessagesEntity>

    @Query("SELECT COUNT(*) FROM Messages")
    override suspend fun size(): Int
}
