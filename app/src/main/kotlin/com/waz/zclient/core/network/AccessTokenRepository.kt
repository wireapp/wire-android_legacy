package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

//TODO: Add Preferences Manager as a collaborator
//TODO: Keep in mind that there should be one preference file per user.
class AccessTokenRepository {

    fun accessToken(): String {
        //TODO: retrieve token, maybe from User Preferences?
        return "mytoken"
    }

    fun updateAccessToken(newToken: String) {
        //TODO: Save the token somewhere: User Preferences?
    }

    fun refreshToken(): String {
        //TODO: retrieve refresh token, maybe from User Preferences?
        return "mytoken"
    }

    fun updateRefreshToken(newRefreshToken: String) {
        //TODO: Save the refresh token somewhere: User Preferences?
    }

    fun renewAccessToken(refreshToken: String) : Either<Failure, String> {
        //TODO: send a network request w/ the refreshToken to get a new accessToken
        return Either.Right("newToken")
    }

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
