package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface MessagesDao : BatchDao<MessagesEntity> {
    @Query("SELECT * FROM Messages")
    suspend fun allMessages(): List<MessagesEntity>

    @Insert
    override suspend fun insert(item: MessagesEntity)

    @Query("SELECT * FROM Messages ORDER BY _id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<MessagesEntity>?

    @Query("SELECT COUNT(*) FROM Messages")
    override suspend fun count(): Int
}
