package com.waz.zclient.core.network.connection

import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion

class ConnectionSpecsFactory private constructor() {

    companion object {

        fun createConnectionSpecs() = listOf(
            createModernConnectionSpec(),
            ConnectionSpec.CLEARTEXT
        )

        private fun createModernConnectionSpec(): ConnectionSpec =
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .cipherSuites(
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
                )
                .build()

    }
}
