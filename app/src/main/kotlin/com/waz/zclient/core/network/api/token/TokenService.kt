package com.waz.zclient.core.network.api.token

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService

class TokenService(private val tokenApi: TokenApi, private val apiService: ApiService) {

    //TODO: do we always need defaults?
    fun renewAccessToken(refreshToken: String): Either<Failure, AccessTokenResponse> =
        apiService.request(
            tokenApi.access(mapOf(
                "Cookie" to "zuid=$refreshToken"
            )),
            AccessTokenResponse.EMPTY
        )
}
