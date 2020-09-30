package com.waz.zclient.core.network.proxy

import android.content.Context
import android.content.pm.PackageManager
import com.waz.zclient.BuildConfig
import com.waz.zclient.core.logging.Logger
import java.net.InetSocketAddress
import java.net.Proxy

data class ProxyDetails(val hostUrl: String, val port: String) {
    val hostAndPort: Pair<String, Int>? =
        if (hostUrl.equals(HttpProxy.INVALID_PROXY_HOST, ignoreCase = true)) {
            null
        } else {
            port.toIntOrNull()?.let { Pair(hostUrl, it) }
        }
}

class HttpProxy(private val context: Context? = null, private val proxyDetails: ProxyDetails? = null) {

    val proxy: Proxy? by lazy {
        getDetails().hostAndPort?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.first, it.second)) }
    }

    private fun getDetails() =
        if (proxyDetails != null) proxyDetails
        else if (context != null) {
            try {
                val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
                // empty metadata should be treated the same way as if we didn't find metadata at all
                val url = metadata.getString(HTTP_PROXY_URL_KEY, "")
                val port = metadata.getString(HTTP_PROXY_PORT_KEY, "")
                Logger.verbose(TAG, "HTTP Proxy Details: $url:$port")
                if (url.isNotEmpty() && port.isNotEmpty()) {
                    ProxyDetails(url, port)
                } else {
                    DefaultProxyDetails
                }
            } catch (ex: PackageManager.NameNotFoundException) {
                Logger.error(TAG, "Unable to load metadata: ${ex.message}")
                DefaultProxyDetails
            }
        } else {
            DefaultProxyDetails
        }

    companion object {
        private const val TAG = "HttpProxy"

        const val INVALID_PROXY_HOST = "none"
        private const val HTTP_PROXY_URL_KEY = "http_proxy_url"
        private const val HTTP_PROXY_PORT_KEY = "http_proxy_port"

        val DefaultProxyDetails = ProxyDetails(BuildConfig.HTTP_PROXY_URL, BuildConfig.HTTP_PROXY_PORT)
    }
}
