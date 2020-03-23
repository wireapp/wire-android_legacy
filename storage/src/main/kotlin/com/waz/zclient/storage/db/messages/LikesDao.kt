package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface LikesDao {
    @Query("SELECT * FROM Likings")
    suspend fun allLikes(): List<LikesEntity>
}
