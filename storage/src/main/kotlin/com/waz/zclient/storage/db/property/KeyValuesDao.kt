package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface KeyValuesDao : BatchDao<KeyValuesEntity> {

    @Query("SELECT * FROM KeyValues")
    suspend fun allKeyValues(): List<KeyValuesEntity>

    @Insert
    fun insert(keyValuesEntity: KeyValuesEntity)

    @Query("SELECT * FROM KeyValues ORDER BY key LIMIT :batchSize OFFSET :offset")
    override suspend fun batch(offset: Int, batchSize: Int): List<KeyValuesEntity>?

    @Query("SELECT COUNT(*) FROM KeyValues")
    override suspend fun size(): Int
}
