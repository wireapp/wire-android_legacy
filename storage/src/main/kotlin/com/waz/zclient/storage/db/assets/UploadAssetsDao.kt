package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UploadAssetsDao {

    @Query("SELECT * FROM UploadAssets")
    suspend fun allUploadAssets(): List<UploadAssetsEntity>

    @Insert
    suspend fun insertUploadAsset(asset: UploadAssetsEntity)
}
