package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AssetsV1Dao {

    @Query("SELECT * FROM Assets")
    suspend fun allAssets(): List<AssetsV1Entity>

    @Insert
    suspend fun insertAsset(asset: AssetsV1Entity)
}
