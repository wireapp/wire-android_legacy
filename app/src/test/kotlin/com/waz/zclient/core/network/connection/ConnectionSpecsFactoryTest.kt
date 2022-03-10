package com.waz.zclient.core.network.connection

import com.waz.zclient.UnitTest
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.junit.Test

class ConnectionSpecsFactoryTest : UnitTest() {

    @Test
    fun `Given connectionSpecs are created, then ensure list contains two specs`() {
        val connectionSpecs = ConnectionSpecsFactory.create()
        assert(connectionSpecs.size == 2)
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    // Make sure OkHttp's connectionSpecs can be used to check the HTTPS connection is TLS 1.2 and uses good cipher suites
    fun `Given connectionSpecs are created, then ensure list contains modern specification`() {
        val connectionSpecs = ConnectionSpecsFactory.create()
        with(connectionSpecs[0]) {
            tlsVersions()?.let {
                assert(it[0] == TlsVersion.TLS_1_2)
            }

            cipherSuites()?.let {
                assert(it[0] == CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
                assert(it[1] == CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384)
            }
        }
    }

    @Test
    fun `Given connectionSpecs are created, then list should container CLEARTEXT`() {
        val connectionSpecs = ConnectionSpecsFactory.create()
        assert(connectionSpecs[1] == ConnectionSpec.CLEARTEXT)
    }
}
