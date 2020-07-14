package com.waz.zclient.storage.db.buttons

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ButtonDao {

    @Query("SELECT * FROM Buttons")
    suspend fun allButtons(): List<ButtonEntity>

    @Query("SELECT * FROM Buttons ORDER BY message_id, button_id LIMIT :batchSize OFFSET :offset")
    suspend fun getButtonsInBatch(batchSize: Int, offset: Int): List<ButtonEntity>
}
