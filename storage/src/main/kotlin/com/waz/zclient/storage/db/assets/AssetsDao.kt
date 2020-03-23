package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AssetsDao {

    @Query("SELECT * FROM Assets2")
    suspend fun allAssets(): List<AssetsEntity>

    @Insert
    suspend fun insertAsset(asset: AssetsEntity)
}
