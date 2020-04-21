package com.waz.zclient.shared.assets.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.assets.AssetsRepository
import java.io.InputStream

class GetPublicAssetUseCase(private val assetsRepository: AssetsRepository) : UseCase<InputStream, PublicAsset>() {

    override suspend fun run(params: PublicAsset): Either<Failure, InputStream> =
        assetsRepository.publicAsset(params.assetId)
}

open class PublicAsset(val assetId: String)
