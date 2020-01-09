package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class AccessTokenRepository(private val remoteDataSource: AccessTokenRemoteDataSource,
                            private val localDataSource: AccessTokenLocalDataSource,
                            private val mapper: AccessTokenMapper,
                            private val refreshTokenMapper: RefreshTokenMapper) {

    fun accessToken(): AccessToken? = localDataSource.accessToken()?.let { mapper.from(it) }

    fun updateAccessToken(newToken: AccessToken) =
        localDataSource.updateAccessToken(mapper.toPreference(newToken))

    fun refreshToken(): RefreshToken? = localDataSource.refreshToken()?.let { refreshTokenMapper.from(it) }

    fun updateRefreshToken(newRefreshToken: RefreshTokenResponse) =
        localDataSource.updateRefreshToken(refreshTokenMapper.responseToPref(newRefreshToken))

    fun renewAccessToken(refreshToken: String): Either<Failure, AccessToken> =
        remoteDataSource.renewAccessToken(refreshToken).map { mapper.from(it) }

    fun wipeOutTokens() {
        wipeOutAccessToken()
        wipeOutRefreshToken()
    }

    private fun wipeOutAccessToken() = localDataSource.wipeOutAccessToken()

    private fun wipeOutRefreshToken() = localDataSource.wipeOutRefreshToken()
}
