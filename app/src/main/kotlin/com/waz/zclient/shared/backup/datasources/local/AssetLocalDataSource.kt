package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.assets.AssetsDao
import com.waz.zclient.storage.db.assets.AssetsEntity

class AssetLocalDataSource(private val assetsDao: AssetsDao) {
    suspend fun getAllAssets(): List<AssetsEntity> = assetsDao.allAssets()
}