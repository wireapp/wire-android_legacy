package com.waz.zclient.core.network.useragent

import com.waz.zclient.core.config.AppVersionNameConfig
import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(
    private val userAgentConfig: UserAgentConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        when (chain.request().header(USER_AGENT_HEADER_KEY)) {
            null, String.empty() -> determineUserAgentRequest(chain)
            else -> chain.proceed(chain.request())
        }

    private fun determineUserAgentRequest(chain: Interceptor.Chain) =
        chain.proceed(chain.request()
            .newBuilder()
            .addHeader(USER_AGENT_HEADER_KEY, newUserAgentHeader())
            .build())

    private fun newUserAgentHeader() =
        "${androidVersion()} / ${wireVersion()} / ${httpVersion()}"

    private fun androidVersion(): String =
        "Android ${userAgentConfig.androidVersion}"

    private fun wireVersion(): String =
        "Wire ${userAgentConfig.appVersionNameConfig.versionName}"

    private fun httpVersion(): String =
        "HttpLibrary ${userAgentConfig.httpUserAgent}"

    companion object {
        private const val USER_AGENT_HEADER_KEY = "User-Agent"
    }
}

data class UserAgentConfig(
    val appVersionNameConfig: AppVersionNameConfig,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val httpUserAgent: String = okhttp3.internal.Version.userAgent()
)
