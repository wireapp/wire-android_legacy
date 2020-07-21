package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface AssetsDao : BatchReader<AssetsEntity> {

    @Query("SELECT * FROM Assets2")
    suspend fun allAssets(): List<AssetsEntity>

    @Insert
    suspend fun insertAsset(asset: AssetsEntity)

    @Query("SELECT * FROM Assets2 ORDER BY _id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<AssetsEntity>?

    @Query("SELECT COUNT(*) FROM Assets2")
    override suspend fun size(): Int
}
