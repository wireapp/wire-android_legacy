package com.waz.zclient.core.network.pinning

import com.waz.zclient.UnitTest
import com.waz.zclient.core.utilities.base64.Base64Transformer
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.security.MessageDigest

class PinGeneratorTest : UnitTest() {

    private lateinit var pinGenerator: CertificatePinnerFactory.PinGenerator

    @Mock
    private lateinit var messageDigest: MessageDigest

    @Mock
    private lateinit var base64Transformer: Base64Transformer

    @Before
    fun setup() {
        pinGenerator = CertificatePinnerFactory.PinGenerator(messageDigest, base64Transformer)
    }

    @Test
    fun `Given pin is generated, when certificate is digested, then return pin that related to CertificatePinner restrictions`() {
        `when`(messageDigest.digest(oldCert)).thenReturn(publicKey)
        `when`(base64Transformer.encode(publicKey)).thenReturn(NEW_CERTIFICATE)

        val pin = pinGenerator.pin(oldCert)
        assert(pin == "$PINS$NEW_CERTIFICATE")
    }

    companion object {
        private const val PINS = "sha256/"
        private const val NEW_CERTIFICATE = "NewCert"
        private val oldCert = byteArrayOf(64)
        private val publicKey = byteArrayOf(64)
    }
}
