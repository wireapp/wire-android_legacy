package com.waz.zclient.core.backend

import ProductionBackendItem
import com.waz.zclient.BuildConfig
import com.waz.zclient.core.backend.items.QaBackendItem
import com.waz.zclient.core.backend.items.StagingBackendItem
import com.waz.zclient.core.network.pinning.CertificatePin
import com.waz.zclient.core.utilities.base64.Base64Transformer

//TODO: FirebaseOptions?
data class BackendItem(
    val environment: String,
    val baseUrl: String,
    val websocketUrl: String,
    val blacklistHost: String,
    val teamsUrl: String,
    val accountsUrl: String,
    val websiteUrl: String,
    private val certificatePin: CertificatePin? = null
) {

    fun certificatePin() = certificatePin ?: defaultCertificatePin()

    companion object {
        private fun defaultCertificatePin(): CertificatePin {
            val base64Transformer = Base64Transformer()
            val certBytes = base64Transformer.decode(BuildConfig.CERTIFICATE_PIN_BYTES)
            return CertificatePin(BuildConfig.CERTIFICATE_PIN_DOMAIN, certBytes)
        }
    }
}

class BackendClient {

    private val clients = mapOf(
        Backend.QA.environment to QaBackendItem,
        Backend.PRODUCTION.environment to ProductionBackendItem,
        Backend.STAGING.environment to StagingBackendItem
    )

    fun get(environment: String): BackendItem = clients[environment] ?: ProductionBackendItem
}

enum class Backend(val environment: String) {
    STAGING("staging"),
    QA("qa-demo"),
    PRODUCTION("prod")
}
