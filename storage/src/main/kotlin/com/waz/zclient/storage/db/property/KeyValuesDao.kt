package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface KeyValuesDao : BatchReader<KeyValuesEntity> {

    @Query("SELECT * FROM KeyValues")
    suspend fun allKeyValues(): List<KeyValuesEntity>

    @Insert
    fun insert(keyValuesEntity: KeyValuesEntity)

    @Insert
    fun insert(keyValuesEntityList: List<KeyValuesEntity>)

    @Query("SELECT * FROM KeyValues ORDER BY key LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<KeyValuesEntity>

    @Query("SELECT COUNT(*) FROM KeyValues")
    override suspend fun size(): Int
}
