package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import com.waz.zclient.core.network.api.token.TokenService

class AccessTokenRemoteDataSource(private val tokenService: TokenService) {

    suspend fun renewAccessToken(refreshToken: String): Either<Failure, AccessTokenResponse> =
        tokenService.renewAccessToken(refreshToken)
}
