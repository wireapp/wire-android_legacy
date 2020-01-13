package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class AuthTokenHandler(private val tokenRepository: AccessTokenRepository) {

    fun accessToken() = tokenRepository.accessToken()
    fun updateAccessToken(newAccessToken: String) = tokenRepository.updateAccessToken(newAccessToken)

    fun refreshToken() = tokenRepository.refreshToken()
    fun updateRefreshToken(newRefreshToken: String) = tokenRepository.updateRefreshToken(newRefreshToken)

    fun renewAccessToken(refreshToken: String): Either<Failure, String> =
        tokenRepository.renewAccessToken(refreshToken).map { it.token }

    fun wipeOutTokens() = tokenRepository.wipeOutTokens()
}
