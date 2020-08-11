package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface KeyValuesDao : BatchDao<KeyValuesEntity> {

    @Query("SELECT * FROM KeyValues")
    suspend fun allKeyValues(): List<KeyValuesEntity>

    @Query("SELECT * FROM KeyValues ORDER BY `key` LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<KeyValuesEntity>?

    @Query("SELECT COUNT(*) FROM KeyValues")
    override suspend fun count(): Int
}
