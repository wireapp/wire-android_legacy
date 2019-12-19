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
class AccessTokenAuthenticator(private val authToken: AuthToken) : Authenticator {

    /**
     * This authenticate() method is called when server returns 401 Unauthorized.
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to have a token in order to refresh it.
        val token= authToken.refreshToken()

        synchronized(this) {
            val newToken = authToken.accessToken() //TODO: get the new token and save it
            authToken.updateAccessToken(newToken)

            // Check if the request made was previously made as an authenticated request.
            response.request().header(AuthToken.AUTH_HEADER)?.let {

                // If the token has changed since the request was made, use the new token.
                if (newToken != token) {
                    return response.request()
                        .newBuilder()
                        .removeHeader(AuthToken.AUTH_HEADER)
                        //TODO: Extract this line since it is being repeated.
                        .addHeader(AuthToken.AUTH_HEADER, "${AuthToken.AUTH_HEADER_TOKEN_TYPE} $token")
                        .build()
                }

                val updatedToken = authToken.refreshToken()

                // Retry the request with the new token.
                return response.request()
                    .newBuilder()
                    .removeHeader(AuthToken.AUTH_HEADER)
                    //TODO: Extract this line since it is being repeated.
                    .addHeader(AuthToken.AUTH_HEADER, "${AuthToken.AUTH_HEADER_TOKEN_TYPE} $updatedToken")
                    .build()
            }
        }
        return null
    }
}
