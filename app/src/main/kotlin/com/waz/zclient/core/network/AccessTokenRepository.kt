package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.api.token.AccessTokenResponse

//TODO: Add Preferences Manager as a collaborator
//TODO: Keep in mind that there should be one preference file per user.
class AccessTokenRepository(private val remoteDataSource: AccessTokenRemoteDataSource) {

    fun accessToken(): String {
        //TODO: retrieve token, maybe from User Preferences?
        return "myAccessToken"
    }

    fun updateAccessToken(newToken: String) {
        //TODO: Save the token somewhere: User Preferences?
    }

    fun refreshToken(): String {
        //TODO: retrieve refresh token, maybe from User Preferences?
        return "myRefreshToken"
    }

    fun updateRefreshToken(newRefreshToken: String) {
        //TODO: Save the refresh token somewhere: User Preferences?
    }

    //TODO: do we need an intermediary Access Token repository model here?
    fun renewAccessToken(refreshToken: String) : Either<Failure, AccessTokenResponse> =
        remoteDataSource.renewAccessToken(refreshToken)

    fun wipeOutTokens() {
        wipeOutAccessToken()
        wipeOutRefreshToken()
    }

    private fun wipeOutAccessToken() {
        //TODO: clean up access token from the preferences or any storage
    }

    private fun wipeOutRefreshToken() {
        //TODO: clean up refresh token from the preferences or any storage
    }
}
