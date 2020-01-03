package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

class AuthTokenHandler(private val tokenRepository: AccessTokenRepository) {

    companion object {
        const val AUTH_HEADER = "Authorization"
        const val AUTH_HEADER_TOKEN_TYPE = "Bearer"
    }

    fun accessToken() = tokenRepository.accessToken()
    fun updateAccessToken(newAccessToken: String) = tokenRepository.updateAccessToken(newAccessToken)

    fun refreshToken() = tokenRepository.refreshToken()
    fun updateRefreshToken(newRefreshToken: String) = tokenRepository.updateRefreshToken(newRefreshToken)

    fun renewAccessToken(refreshToken: String): Either<Failure, String> =
        tokenRepository.renewAccessToken(refreshToken)

    fun wipeOutTokens() = tokenRepository.wipeOutTokens()
}

