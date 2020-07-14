package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query

@Dao
interface LikesDao {
    @Query("SELECT * FROM Likings")
    suspend fun allLikes(): List<LikesEntity>

    @Query("SELECT * FROM Likings ORDER BY message_id, user_id LIMIT :batchSize OFFSET :offset")
    suspend fun getLikesInBatch(batchSize: Int, offset: Int): List<LikesEntity>
}
