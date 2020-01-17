package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class AccessTokenRepository(private val remoteDataSource: AccessTokenRemoteDataSource,
                            private val localDataSource: AccessTokenLocalDataSource,
                            private val accessTokenMapper: AccessTokenMapper,
                            private val refreshTokenMapper: RefreshTokenMapper) {

    suspend fun accessToken(): AccessToken =
        localDataSource.accessToken()?.let { accessTokenMapper.from(it) } ?: AccessToken.EMPTY

    suspend fun updateAccessToken(newToken: AccessToken) =
        localDataSource.updateAccessToken(accessTokenMapper.toEntity(newToken))

    suspend fun refreshToken(): RefreshToken =
        localDataSource.refreshToken()?.let { refreshTokenMapper.fromTokenText(it) }
            ?: RefreshToken.EMPTY

    suspend fun updateRefreshToken(newRefreshToken: RefreshToken) =
        localDataSource.updateRefreshToken(refreshTokenMapper.toEntity(newRefreshToken))

    suspend fun renewAccessToken(refreshToken: RefreshToken): Either<Failure, AccessToken> =
        remoteDataSource.renewAccessToken(refreshToken.token).map { accessTokenMapper.from(it) }
}
