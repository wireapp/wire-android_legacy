package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MessagesDao {
    @Query("SELECT * FROM Messages")
    suspend fun allMessages(): List<MessagesEntity>
}
