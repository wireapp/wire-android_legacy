package com.waz.zclient.assets

import com.waz.zclient.assets.datasources.AssetsRemoteDataSource
import com.waz.zclient.assets.mapper.AssetMapper
import com.waz.zclient.core.functional.map

class AssetsRepository(
    private val assetsRemoteDataSource: AssetsRemoteDataSource,
    private val assetMapper: AssetMapper
) {

    suspend fun publicAsset(assetId: String) = assetsRemoteDataSource.publicAsset(assetId).map {
        assetMapper.toInputStream(it)
    }
}
