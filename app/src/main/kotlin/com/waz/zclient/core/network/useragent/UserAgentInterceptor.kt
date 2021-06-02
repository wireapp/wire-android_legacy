package com.waz.zclient.core.network.useragent

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class UserAgentInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        when (chain.request().header(USER_AGENT_HEADER_KEY)) {
            null -> chain.proceed(chain.request())
            else -> chain.proceed(removeUserAgentHeader(chain))
        }

    private fun removeUserAgentHeader(chain: Interceptor.Chain): Request =
        chain.request().newBuilder().removeHeader(USER_AGENT_HEADER_KEY).build()

    companion object {
        private const val USER_AGENT_HEADER_KEY = "User-Agent"
    }
}
