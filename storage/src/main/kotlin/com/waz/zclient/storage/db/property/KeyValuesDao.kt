package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface KeyValuesDao {

    @Query("SELECT * FROM KeyValues")
    suspend fun allKeyValues(): List<KeyValuesEntity>

    @Insert
    fun insert(keyValuesEntity: KeyValuesEntity)

    @Insert
    fun insert(keyValuesEntityList: List<KeyValuesEntity>)

    @Query("SELECT * FROM KeyValues ORDER BY key LIMIT :batchSize OFFSET :offset")
    suspend fun getKeyValuesInBatch(batchSize: Int, offset: Int): List<KeyValuesEntity>
}
