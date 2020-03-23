package com.waz.zclient.storage.db.assets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DownloadAssetsDao {

    @Query("SELECT * FROM DownloadAssets")
    suspend fun allDownloadAssets(): List<DownloadAssetsEntity>

    @Insert
    suspend fun insertDownloadAsset(downloadAsset: DownloadAssetsEntity)
}
