package com.waz.zclient.core.network

import com.waz.zclient.core.functional.Either
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
class AccessTokenAuthenticator(private val authToken: AuthToken) : Authenticator {

    /**
     * This authenticate() method is called when server returns 401 Unauthorized.
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = authToken.refreshToken()

        synchronized(this) {
            val tokenResult = authToken.renewAccessToken(refreshToken)

            return when (tokenResult) {
                is Either.Left -> null
                is Either.Right -> {
                    authToken.updateAccessToken(tokenResult.b)
                    proceedWithNewAccessToken(response, tokenResult.b)
                }
            }

        }
    }

    private fun proceedWithNewAccessToken(response: Response, newAccessToken: String): Request? =
        response.request().header(AuthToken.AUTH_HEADER)?.let {
            response.request()
                .newBuilder()
                .removeHeader(AuthToken.AUTH_HEADER)
                .addHeader(AuthToken.AUTH_HEADER, "${AuthToken.AUTH_HEADER_TOKEN_TYPE} $newAccessToken")
                .build()
        }

}
