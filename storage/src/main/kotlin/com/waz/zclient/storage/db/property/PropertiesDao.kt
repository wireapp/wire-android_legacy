package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface PropertiesDao : BatchReader<PropertiesEntity> {
    @Query("SELECT * FROM Properties")
    suspend fun allProperties(): List<PropertiesEntity>

    @Query("SELECT * FROM Properties ORDER BY key LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<PropertiesEntity>

    @Query("SELECT COUNT(*) FROM Properties")
    override suspend fun size(): Int
}
