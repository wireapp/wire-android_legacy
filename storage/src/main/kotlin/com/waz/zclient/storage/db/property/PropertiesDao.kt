package com.waz.zclient.storage.db.property

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PropertiesDao {
    @Query("SELECT * FROM Properties")
    suspend fun allProperties(): List<PropertiesEntity>
}
