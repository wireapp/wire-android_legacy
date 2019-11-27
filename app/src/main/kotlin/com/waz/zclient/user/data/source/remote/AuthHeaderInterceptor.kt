package com.waz.zclient.user.data.source.remote

import okhttp3.Interceptor
import okhttp3.Response

object AuthHeaderInterceptor : Interceptor {
    @JvmStatic
    var token: String? = null

    @JvmStatic
    var tokenType: String? = null

    override fun intercept(chain: Interceptor.Chain): Response = chain.run {
        val request = request().newBuilder().apply {
            token?.let { addHeader("Authorization", "$tokenType $it") }
        }.build()
        proceed(request)
    }
}
