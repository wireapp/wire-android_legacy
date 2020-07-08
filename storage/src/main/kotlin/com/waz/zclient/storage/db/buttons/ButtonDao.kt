package com.waz.zclient.storage.db.buttons

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ButtonDao {

    @Query("SELECT * FROM Buttons")
    suspend fun allButtons(): List<ButtonEntity>
}