package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class AccessTokenRepository(
    private val remoteDataSource: AccessTokenRemoteDataSource,
    private val localDataSource: AccessTokenLocalDataSource,
    private val accessTokenMapper: AccessTokenMapper,
    private val refreshTokenMapper: RefreshTokenMapper
) {

    fun accessToken(): AccessToken =
        localDataSource.accessToken()?.let { accessTokenMapper.from(it) } ?: AccessToken.EMPTY

    fun updateAccessToken(newToken: AccessToken) =
        localDataSource.updateAccessToken(accessTokenMapper.toPreference(newToken))

    fun refreshToken(): RefreshToken =
        localDataSource.refreshToken()?.let { refreshTokenMapper.from(it) } ?: RefreshToken.EMPTY

    fun updateRefreshToken(newRefreshToken: RefreshToken) =
        localDataSource.updateRefreshToken(refreshTokenMapper.toPreference(newRefreshToken))

    suspend fun renewAccessToken(refreshToken: RefreshToken): Either<Failure, AccessToken> =
        remoteDataSource.renewAccessToken(refreshToken.token).map { accessTokenMapper.from(it) }

    fun wipeOutTokens() {
        wipeOutAccessToken()
        wipeOutRefreshToken()
    }

    private fun wipeOutAccessToken() = localDataSource.wipeOutAccessToken()

    private fun wipeOutRefreshToken() = localDataSource.wipeOutRefreshToken()
}
