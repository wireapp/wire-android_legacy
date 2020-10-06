package com.waz.zclient.core.network.proxy

import com.waz.zclient.KotlinServices
import com.waz.zclient.core.logging.Logger
import java.net.Proxy

class HttpProxy() {
    fun proxy(): Proxy? = KotlinServices.httpProxy.also { proxy ->
        Logger.verbose("HttpProxy","HTTP Proxy: ${proxy?.address()}")
    }
}
