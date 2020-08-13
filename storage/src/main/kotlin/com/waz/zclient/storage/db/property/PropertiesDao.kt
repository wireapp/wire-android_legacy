package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface PropertiesDao : BatchDao<PropertiesEntity> {
    @Query("SELECT * FROM Properties")
    suspend fun allProperties(): List<PropertiesEntity>

    @Query("SELECT * FROM Properties ORDER BY `key` LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<PropertiesEntity>?

    @Query("SELECT COUNT(*) FROM Properties")
    override suspend fun count(): Int
}
