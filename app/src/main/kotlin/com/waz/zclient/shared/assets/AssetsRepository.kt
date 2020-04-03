package com.waz.zclient.shared.assets

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import java.io.InputStream

interface AssetsRepository {
    suspend fun publicAsset(assetId: String): Either<Failure, InputStream>
}
