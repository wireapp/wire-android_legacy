package com.waz.zclient.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Authenticator that attempts to refresh the client's access token.
 * In the event that a refresh fails and a new token can't be issued an error
 * is delivered to the caller. This authenticator blocks all requests while a token
 * refresh is being performed. In-flight requests that fail with a 401 (unauthorized)
 * are automatically retried.
 */
class AccessTokenAuthenticator(private val authTokenHandler: AuthTokenHandler) : Authenticator {

    companion object {
        const val AUTH_HEADER = "Authorization"
        const val AUTH_HEADER_TOKEN_TYPE = "Bearer"
    }

    /**
     * This authenticate() method is called when server returns 401 Unauthorized.
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        updateRefreshToken(response)
        val refreshToken = authTokenHandler.refreshToken()

        synchronized(this) {
            val tokenResult = authTokenHandler.renewAccessToken(refreshToken)

            return tokenResult.fold({ null }) {
                authTokenHandler.updateAccessToken(it)
                proceedWithNewAccessToken(response, it.token)
            }
        }
    }

    private fun proceedWithNewAccessToken(response: Response, newAccessToken: String): Request? =
        response.request().header(AUTH_HEADER)?.let {
            response.request()
                .newBuilder()
                .removeHeader(AUTH_HEADER)
                .addHeader(AUTH_HEADER, "$AUTH_HEADER_TOKEN_TYPE $newAccessToken")
                .build()
        }

    private fun updateRefreshToken(response: Response) =
        response.headers()["Cookie"]?.let {
            if (authTokenHandler.refreshToken() != it) {
                authTokenHandler.updateRefreshToken(it)
            }
        }
}
