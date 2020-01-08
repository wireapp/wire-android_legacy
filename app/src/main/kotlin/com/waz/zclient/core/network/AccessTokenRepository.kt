package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.api.token.AccessTokenResponse

class AccessTokenRepository(private val remoteDataSource: AccessTokenRemoteDataSource,
                            private val localDataSource: AccessTokenLocalDataSource) {

    fun accessToken(): String = localDataSource.accessToken() ?: String.empty()

    fun updateAccessToken(newToken: String) = localDataSource.updateAccessToken(newToken)

    fun refreshToken(): String = localDataSource.refreshToken() ?: String.empty()

    fun updateRefreshToken(newRefreshToken: String) =
        localDataSource.updateRefreshToken(newRefreshToken)

    //TODO: do we need an intermediary Access Token repository model here?
    fun renewAccessToken(refreshToken: String): Either<Failure, AccessTokenResponse> =
        remoteDataSource.renewAccessToken(refreshToken)

    fun wipeOutTokens() {
        wipeOutAccessToken()
        wipeOutRefreshToken()
    }

    private fun wipeOutAccessToken() = localDataSource.wipeOutAccessToken()

    private fun wipeOutRefreshToken() = localDataSource.wipeOutRefreshToken()
}
