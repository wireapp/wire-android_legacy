package com.waz.zclient.core.network.proxy

import com.waz.zclient.BuildConfig
import java.net.InetSocketAddress
import java.net.Proxy

class HttpProxyFactory private constructor() {

    companion object {
        private const val INVALID_PROXY_HOST = "none"

        fun create(proxyDetails: ProxyDetails = ProxyDetails()): Proxy? {
            val proxyHost = parseHost(proxyDetails.hostUrl)
            val proxyPort = proxyDetails.port.toIntOrNull()
            return when {
                proxyHost != null && proxyPort != null -> {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
                }
                else -> null
            }
        }

        private fun parseHost(proxyHostUrl: String) =
            if (proxyHostUrl.equals(INVALID_PROXY_HOST, ignoreCase = true)) {
                null
            } else proxyHostUrl
    }
}

data class ProxyDetails(
    val hostUrl: String = BuildConfig.HTTP_PROXY_URL,
    val port: String = BuildConfig.HTTP_PROXY_PORT
)
