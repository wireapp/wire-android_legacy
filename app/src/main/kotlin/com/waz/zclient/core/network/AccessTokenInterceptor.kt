package com.waz.zclient.core.network

import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(private val authTokenHandler: AuthTokenHandler) : Interceptor {

    companion object {
        private const val RESPONSE_HEADER_REFRESH_TOKEN_KEY = "Cookie"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authTokenHandler.accessToken()

        return when (token != String.empty()) {
            true -> addAuthHeader(chain, token)
            false -> chain.proceed(chain.request())
        }.also {
            updateRefreshTokenFromResponse(it)
        }
    }

    private fun addAuthHeader(chain: Interceptor.Chain, token: String): Response {
        val authenticatedRequest = chain.request()
            .newBuilder()
            .addHeader(AuthTokenHandler.AUTH_HEADER,
                "${AuthTokenHandler.AUTH_HEADER_TOKEN_TYPE} $token")
            .build()
        return chain.proceed(authenticatedRequest)
    }

    private fun updateRefreshTokenFromResponse(response: Response) =
        response.headers()[RESPONSE_HEADER_REFRESH_TOKEN_KEY]?.let {
            if (authTokenHandler.refreshToken() != it) {
                authTokenHandler.updateRefreshToken(it)
            }
        }
}
