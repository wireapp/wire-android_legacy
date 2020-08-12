package com.waz.zclient.storage.db.buttons

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface ButtonsDao : BatchDao<ButtonsEntity> {

    @Query("SELECT * FROM Buttons")
    suspend fun allButtons(): List<ButtonsEntity>

    @Query("SELECT * FROM Buttons ORDER BY message_id, button_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<ButtonsEntity>?

    @Query("SELECT COUNT(*) FROM Buttons")
    override suspend fun count(): Int
}
