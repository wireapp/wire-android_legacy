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
        request(AccessTokenResponse.EMPTY) {
            tokenApi.access(generateCookieHeader(refreshToken))
        }

    suspend fun logout(refreshToken: String, accessToken: String): Either<Failure, Unit> =
        request { tokenApi.logout(generateCookieHeader(refreshToken), generateTokenQuery(accessToken)) }

    private fun generateTokenQuery(accessToken: String) = mapOf("access_token" to accessToken)

    private fun generateCookieHeader(refreshToken: String) = mapOf("Cookie" to "zuid=$refreshToken")
}
