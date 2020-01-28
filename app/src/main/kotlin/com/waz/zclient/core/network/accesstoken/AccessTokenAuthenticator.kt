package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.core.functional.foldSuspendable
import kotlinx.coroutines.runBlocking
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
class AccessTokenAuthenticator(
    private val repository: AccessTokenRepository,
    private val refreshTokenMapper: RefreshTokenMapper
) : Authenticator {

    companion object {
        const val AUTH_HEADER = "Authorization"
        const val AUTH_HEADER_TOKEN_TYPE = "Bearer"
    }

    /**
     * This authenticate() method is called when server returns 401 Unauthorized.
     */
    override fun authenticate(route: Route?, response: Response): Request? = runBlocking {
        updateRefreshToken(response)
        val refreshToken = repository.refreshToken()

        val tokenResult = repository.renewAccessToken(refreshToken)

        tokenResult.foldSuspendable({ null }) {
            repository.updateAccessToken(it)
            proceedWithNewAccessToken(response, it.token)
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

    private suspend fun updateRefreshToken(response: Response) =
        response.headers()["Cookie"]?.let {
            val newRefreshToken = refreshTokenMapper.fromTokenText(it)
            if (repository.refreshToken() != newRefreshToken) {
                repository.updateRefreshToken(newRefreshToken)
            }
        }
}
