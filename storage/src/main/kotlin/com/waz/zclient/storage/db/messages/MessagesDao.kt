package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MessagesDao {
    @Query("SELECT * FROM Messages")
    suspend fun allMessages(): List<MessagesEntity>

    @Query("SELECT * FROM Messages ORDER BY _id LIMIT :batchSize OFFSET :offset")
    suspend fun getMessagesInBatch(batchSize: Int, offset: Int): List<MessagesEntity>
}
