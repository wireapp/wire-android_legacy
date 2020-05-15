package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MessageContentIndexDao {
    @Query("SELECT * FROM MessageContentIndex")
    suspend fun allMessageContentIndexes(): List<MessageContentIndexEntity>
}
