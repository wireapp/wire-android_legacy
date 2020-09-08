package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface AssetsDao : BatchDao<AssetsEntity> {

    @Query("SELECT * FROM Assets2")
    suspend fun allAssets(): List<AssetsEntity>

    @Query("SELECT * FROM Assets2 ORDER BY _id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<AssetsEntity>?

    @Query("SELECT COUNT(*) FROM Assets2")
    override suspend fun count(): Int
}
