package com.waz.zclient.core.backend

import ProductionBackendItem
import com.waz.zclient.BuildConfig
import com.waz.zclient.core.extension.decodeBase64
import com.waz.zclient.core.network.pinning.CertificatePin

abstract class BackendItem {

    private val certBytes by lazy {
        BuildConfig.CERTIFICATE_PIN_BYTES.decodeBase64()
    }

    private val certPin by lazy {
        CertificatePin(BuildConfig.CERTIFICATE_PIN_DOMAIN, certBytes)
    }

    abstract fun environment(): String

    abstract fun baseUrl(): String

    fun pinningCertificate() = certPin
}

class BackendClient {

    val clients = mapOf(
        Backend.QA.environment to QaBackendItem(),
        Backend.PRODUCTION.environment to ProductionBackendItem(),
        Backend.STAGING.environment to StagingBackendItem()
    )

    fun get(environment: String): BackendItem = clients[environment] ?: ProductionBackendItem()
}


enum class Backend(val environment: String) {
    STAGING("staging"),
    QA("qa-demo"),
    PRODUCTION("prod")
}
