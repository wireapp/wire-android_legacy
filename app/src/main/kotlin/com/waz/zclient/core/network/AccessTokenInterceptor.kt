package com.waz.zclient.core.network

import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(private val authTokenHandler: AuthTokenHandler) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authTokenHandler.accessToken()

        return when (token != String.empty()) {
            true -> addAuthHeader(chain, token)
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
