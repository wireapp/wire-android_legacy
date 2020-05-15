package com.waz.zclient.storage.db.errors

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ErrorsDao {
    @Query("SELECT * FROM Errors")
    suspend fun allErrors(): List<ErrorsEntity>
}
