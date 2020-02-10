package com.waz.zclient.core.network.accesstoken

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(private val repository: AccessTokenRepository) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { repository.accessToken() }

        return when (accessToken != AccessToken.EMPTY) {
            true -> addAuthHeader(chain, accessToken.token)
            false -> chain.proceed(chain.request())
        }
    }

    private fun addAuthHeader(chain: Interceptor.Chain, token: String): Response {
        val authenticatedRequest = chain.request()
            .newBuilder()
            .addHeader(AccessTokenAuthenticator.AUTH_HEADER,
                "${AccessTokenAuthenticator.AUTH_HEADER_TOKEN_TYPE} $token")
            .build()
        return chain.proceed(authenticatedRequest)
    }
}
