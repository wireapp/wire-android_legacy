package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.property.KeyValuesEntity

@Dao
interface MessagesDao {
    @Query("SELECT * FROM Messages")
    suspend fun allMessages(): List<MessagesEntity>

    @Insert
    suspend fun insert(message: MessagesEntity)

    @Query("SELECT * FROM Messages ORDER BY _id LIMIT :batchSize OFFSET :offset")
    suspend fun getBatch(offset: Int, batchSize: Int): List<MessagesEntity>?

    @Query("SELECT COUNT(*) FROM Messages")
    suspend fun size(): Int
}
