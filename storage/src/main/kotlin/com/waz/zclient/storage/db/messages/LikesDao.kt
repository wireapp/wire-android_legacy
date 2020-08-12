package com.waz.zclient.storage.db.messages

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface LikesDao : BatchDao<LikesEntity> {
    @Query("SELECT * FROM Likings")
    suspend fun allLikes(): List<LikesEntity>

    @Query("SELECT * FROM Likings ORDER BY message_id, user_id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<LikesEntity>?

    @Query("SELECT COUNT(*) FROM Likings")
    override suspend fun count(): Int
}
