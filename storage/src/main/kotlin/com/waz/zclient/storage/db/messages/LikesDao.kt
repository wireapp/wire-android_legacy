package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface LikesDao : BatchReader<LikesEntity> {
    @Query("SELECT * FROM Likings")
    suspend fun allLikes(): List<LikesEntity>

    @Query("SELECT * FROM Likings ORDER BY message_id, user_id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<LikesEntity>?

    @Query("SELECT COUNT(*) FROM Likings")
    override suspend fun size(): Int
}
