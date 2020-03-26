package com.waz.zclient.core.network.pinning

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify

class CertificatePinnerFactoryTest : UnitTest() {

    @Mock
    private lateinit var certificationPin: CertificatePin

    @Mock
    private lateinit var pinGenerator: CertificatePinnerFactory.PinGenerator

    @Test
    fun `Given CertificatePinner is generated, when pin is injected, then verify pin is generated`() {
        `when`(certificationPin.certificate).thenReturn(certificate)
        `when`(certificationPin.domain).thenReturn(TEST_DOMAIN)
        lenient().`when`(pinGenerator.pin(certificate)).thenReturn("sha256/")

        CertificatePinnerFactory.create(certificationPin, pinGenerator)

        verify(pinGenerator).pin(eq(certificate))
    }

    companion object {
        private val certificate = byteArrayOf(64)
        private const val TEST_DOMAIN = "www.wire.com"
    }
}
