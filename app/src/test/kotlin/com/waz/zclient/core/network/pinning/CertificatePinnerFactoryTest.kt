package com.waz.zclient.core.network.pinning

import com.waz.zclient.UnitTest
import com.waz.zclient.core.utilities.base64.Base64Transformer
import com.waz.zclient.eq
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import java.security.MessageDigest

private val certificate: ByteArray = arrayOf(
    0x30, 0x82, 0x01, 0x22, 0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01,
    0x05, 0x00, 0x03, 0x82, 0x01, 0x0F, 0x00, 0x30, 0x82, 0x01, 0x0A, 0x02, 0x82, 0x01, 0x01, 0x00, 0xAD,
    0xE6, 0x33, 0x05, 0x6B, 0xAF, 0x9D, 0x52, 0x98, 0x7E, 0x03, 0x4D, 0x5F, 0x77, 0x55, 0x8D, 0x49, 0xEA,
    0x21, 0x5B, 0x65, 0xE1, 0x7A, 0x90, 0x9C, 0x27, 0x18, 0xEA, 0x6F, 0xEC, 0x58, 0xCD, 0x79, 0x4D, 0x32,
    0xB4, 0x6E, 0x8F, 0x45, 0x2A, 0x73, 0x31, 0x34, 0x03, 0xED, 0x0F, 0x30, 0x7A, 0x56, 0x24, 0x6F, 0xA5,
    0x9B, 0x0A, 0x11, 0xE7, 0xD7, 0xF2, 0x98, 0xB5, 0xE4, 0x97, 0x2C, 0xE2, 0xE6, 0x62, 0xB9, 0x9F, 0x35,
    0x21, 0x25, 0x75, 0x6E, 0xAC, 0x6D, 0xA6, 0xC8, 0xC9, 0xE7, 0xA6, 0x23, 0x7A, 0x67, 0xBD, 0xF4, 0x37,
    0x1A, 0xE5, 0xC9, 0x37, 0xE6, 0x94, 0xE1, 0x62, 0xE2, 0xBA, 0x8D, 0x9F, 0x2F, 0x1B, 0xAA, 0x49, 0x89,
    0x8F, 0x66, 0x45, 0x8F, 0x67, 0x19, 0xA7, 0x62, 0x77, 0x8A, 0x96, 0x2F, 0xBB, 0xB0, 0x01, 0xEA, 0x08,
    0x9F, 0x1D, 0xA6, 0x38, 0xCE, 0xC7, 0x7A, 0x90, 0x82, 0x87, 0x63, 0x4E, 0x5D, 0xDF, 0x84, 0x8D, 0x0E,
    0x2C, 0x06, 0xC7, 0xE0, 0x6F, 0xE9, 0x16, 0xAD, 0xC5, 0x50, 0x98, 0x1C, 0xEF, 0x2C, 0x73, 0xD4, 0x0E,
    0x8C, 0xA4, 0x9B, 0xF9, 0xBE, 0x06, 0xF2, 0xF6, 0x78, 0x9D, 0x67, 0x8C, 0xF3, 0x62, 0x33, 0xF1, 0xA8,
    0x79, 0x76, 0xE6, 0x21, 0x6D, 0x56, 0x33, 0x78, 0x9F, 0xB9, 0xC6, 0x09, 0xA0, 0x3F, 0x60, 0xF9, 0x5E,
    0x4D, 0x7C, 0x77, 0xC0, 0x36, 0xB5, 0x6E, 0xDB, 0x0E, 0x18, 0x70, 0xB0, 0xDA, 0x77, 0x40, 0xBC, 0xE4,
    0xD6, 0xFC, 0x53, 0x5B, 0x20, 0x14, 0xD9, 0x2B, 0x80, 0xEA, 0xB3, 0x85, 0xDF, 0xF5, 0xC4, 0x7A, 0x24,
    0xD6, 0xE3, 0xB2, 0x8E, 0x87, 0x8B, 0x4C, 0x81, 0xC7, 0x62, 0x4A, 0xF2, 0xBD, 0xB0, 0x44, 0x99, 0xD9,
    0x7A, 0xBF, 0xE7, 0xF6, 0x27, 0x51, 0x0C, 0xD3, 0xE1, 0x63, 0xF6, 0xFB, 0x1E, 0x23, 0x64, 0xD8, 0xAD,
    0x02, 0x03, 0x01, 0x00, 0x01).map { it.toByte() }.toByteArray()

class CertificatePinnerFactoryTest : UnitTest() {

    @Mock
    private lateinit var certificationPin: CertificatePin

    @Mock
    private lateinit var pinGenerator: CertificatePinnerFactory.PinGenerator

    @Test
    fun `given CertificatePinner is generated, when pin is injected, then verify pin is generated`() {
        `when`(certificationPin.certificate).thenReturn(certificate)
        `when`(certificationPin.domain).thenReturn(TEST_DOMAIN)
        lenient().`when`(pinGenerator.pin(certificate)).thenReturn("sha256/")

        CertificatePinnerFactory.create(certificationPin, pinGenerator)

        verify(pinGenerator).pin(eq(certificate))
    }

    companion object {
        private const val TEST_DOMAIN = "www.wire.com"
    }
}

class PinGeneratorTest : UnitTest() {

    private lateinit var pinGenerator: CertificatePinnerFactory.PinGenerator

    private lateinit var messageDigest: MessageDigest

    private lateinit var base64Transformer: Base64Transformer

    @Before
    fun setup() {
        base64Transformer = Base64Transformer()
        messageDigest = MessageDigest.getInstance(PUBLIC_KEY_ALGORITHM)
        pinGenerator = CertificatePinnerFactory.PinGenerator(messageDigest, base64Transformer)
    }

    @Test
    fun `given pin is generated, when certificate is digested, then return pin that related to CertificatePinner restrictions`() {
        val newCert = base64Transformer.encode(messageDigest.digest(certificate))
        val pin = pinGenerator.pin(certificate)
        assert(pin == "$PINS$newCert")
    }

    companion object {
        private const val PINS = "sha256/"
        private const val PUBLIC_KEY_ALGORITHM = "SHA-256"
    }
}
