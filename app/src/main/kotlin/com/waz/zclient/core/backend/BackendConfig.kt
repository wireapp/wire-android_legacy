package com.waz.zclient.core.backend

import com.waz.service.FirebaseOptions
import com.waz.zclient.core.network.pinning.CertificatePin
import java.net.URI

class BackendConfig {

    fun currentBackend(): Backend? =
        null
}

data class Backend(
    val environment: String,
    val baseUrl: URI,
    val websocketUrl: URI,
    val blacklistHost: URI,
    val teamsUrl: URI,
    val accountsUrl: URI,
    val websiteUrl: URI,
    val firebaseOptions: FirebaseOptions,
    val pin: CertificatePin
)
