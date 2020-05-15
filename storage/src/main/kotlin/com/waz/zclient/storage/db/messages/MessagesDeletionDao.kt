package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MessagesDeletionDao {
    @Query("SELECT * FROM MsgDeletion")
    suspend fun allMessageDeletions(): List<MessageDeletionEntity>
}
