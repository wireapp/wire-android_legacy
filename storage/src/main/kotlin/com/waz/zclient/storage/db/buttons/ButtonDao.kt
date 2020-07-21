package com.waz.zclient.storage.db.buttons

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface ButtonDao : BatchReader<ButtonEntity> {

    @Query("SELECT * FROM Buttons")
    suspend fun allButtons(): List<ButtonEntity>

    @Query("SELECT * FROM Buttons ORDER BY message_id, button_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<ButtonEntity>?

    @Query("SELECT COUNT(*) FROM Buttons")
    override suspend fun size(): Int
}
