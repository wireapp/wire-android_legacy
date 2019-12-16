package com.waz.zclient.core.network

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

    fun renewAccessToken() {
        //TODO: send a network request w/ the refreshToken to get a new accessToken
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
