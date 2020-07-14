package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PropertiesDao {
    @Query("SELECT * FROM Properties")
    suspend fun allProperties(): List<PropertiesEntity>

    @Query("SELECT * FROM Properties ORDER BY key LIMIT :batchSize OFFSET :offset")
    suspend fun getPropertiesInBatch(batchSize: Int, offset: Int): List<PropertiesEntity>
}
