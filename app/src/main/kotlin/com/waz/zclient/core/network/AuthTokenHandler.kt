package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import retrofit2.Response

class AuthTokenHandler(private val tokenRepository: AccessTokenRepository) {

    companion object {
        private const val RESPONSE_HEADER_REFRESH_TOKEN_KEY = "Cookie"

        const val AUTH_HEADER = "Authorization"
        const val AUTH_HEADER_TOKEN_TYPE = "Bearer"
    }

    fun accessToken() = tokenRepository.accessToken()
    fun updateAccessToken(newAccessToken: String) = tokenRepository.updateAccessToken(newAccessToken)

    fun refreshToken() = tokenRepository.refreshToken()
    fun updateRefreshToken(newRefreshToken: String) = tokenRepository.updateRefreshToken(newRefreshToken)

    fun updateRefreshTokenFromResponse(response: Response<*>) =
        response.headers()[RESPONSE_HEADER_REFRESH_TOKEN_KEY]?.let {
            if (refreshToken() != it) {
                updateRefreshToken(it)
            }
        }

    fun renewAccessToken(refreshToken: String): Either<Failure, String> =
        tokenRepository.renewAccessToken(refreshToken).map { it.token }

    fun wipeOutTokens() = tokenRepository.wipeOutTokens()
}
