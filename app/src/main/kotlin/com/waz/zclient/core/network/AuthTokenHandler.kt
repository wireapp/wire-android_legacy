package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either

class AuthTokenHandler(private val tokenRepository: AccessTokenRepository) {

    fun accessToken(): String = tokenRepository.accessToken()?.token ?: String.empty()

    fun updateAccessToken(newAccessToken: AccessToken) = tokenRepository.updateAccessToken(newAccessToken)

    fun refreshToken() = tokenRepository.refreshToken()
    fun updateRefreshToken(newRefreshToken: String) = tokenRepository.updateRefreshToken(newRefreshToken)

    fun renewAccessToken(refreshToken: String): Either<Failure, AccessToken> =
        tokenRepository.renewAccessToken(refreshToken)

    fun wipeOutTokens() = tokenRepository.wipeOutTokens()
}
