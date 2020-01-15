package com.waz.zclient.core.network.api.token

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class TokenService(
    override val networkHandler: NetworkHandler,
    private val tokenApi: TokenApi
) : ApiService() {

    suspend fun renewAccessToken(refreshToken: String): Either<Failure, AccessTokenResponse> =
        request({
            tokenApi.access(mapOf("Cookie" to "zuid=$refreshToken"))
        }, AccessTokenResponse.EMPTY)
}
