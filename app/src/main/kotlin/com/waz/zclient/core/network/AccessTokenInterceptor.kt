package com.waz.zclient.core.network

import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(private val authToken: AuthToken) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authToken.accessToken()

        return when (token != String.empty()) {
            true -> {
                val authenticatedRequest = chain.request()
                .newBuilder()
                .addHeader(AuthToken.AUTH_HEADER, "${AuthToken.AUTH_HEADER_TOKEN_TYPE} $token")
                .build()
                chain.proceed(authenticatedRequest)
            }
            false -> chain.proceed(chain.request())
        }
    }
}
