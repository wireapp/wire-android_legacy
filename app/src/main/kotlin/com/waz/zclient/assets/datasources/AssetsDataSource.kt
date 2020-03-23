package com.waz.zclient.assets.datasources

import com.waz.zclient.assets.AssetsRepository
import com.waz.zclient.assets.mapper.AssetMapper
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import java.io.InputStream

class AssetsDataSource(
    private val assetsRemoteDataSource: AssetsRemoteDataSource,
    private val assetMapper: AssetMapper
) : AssetsRepository {

    override suspend fun publicAsset(assetId: String): Either<Failure, InputStream> =
        assetsRemoteDataSource.publicAsset(assetId).map {
            assetMapper.toInputStream(it)
        }
}
