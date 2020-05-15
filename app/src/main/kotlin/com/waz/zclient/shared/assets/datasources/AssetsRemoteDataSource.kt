package com.waz.zclient.shared.assets.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.shared.assets.AssetsApi
import okhttp3.ResponseBody

class AssetsRemoteDataSource(
    private val assetsApi: AssetsApi,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun publicAsset(assetId: String): Either<Failure, ResponseBody> = request { assetsApi.publicAsset(assetId) }
}
