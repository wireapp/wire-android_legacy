package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.crypto.encryption.error.HashWrongSize
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.UnsatisfiedLink
import com.waz.zclient.framework.functional.assertLeft
import com.waz.zclient.framework.functional.assertRight
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class CryptoTest : UnitTest() {

    private lateinit var crypto: Crypto

    @Mock
    private lateinit var cryptoWrapper: CryptoWrapper

    @Before
    fun setup() {
        crypto = Crypto(cryptoWrapper)
    }

    @Test
    fun `given loadLibrary is success, then assert success is propagated`() {
        val res = crypto.loadLibrary

        res.assertRight()
    }

    @Test
    fun `given loadLibrary fails with UnsatisfiedLinkError, then assert UnsatisfiedLink  is propagates`() {
        `when`(cryptoWrapper.loadLibrary()).thenThrow(UnsatisfiedLinkError::class.java)

        val res = crypto.loadLibrary

        res.assertLeft {
            assertEquals(it, UnsatisfiedLink)
        }
    }

    @Test
    fun `given generateSalt is called, then return correct size ByteArray`() {
        val saltSize = SALT_SIZE

        `when`(cryptoWrapper.pWhashSaltBytes()).thenReturn(saltSize)

        val res = crypto.generateSalt()

        verify(cryptoWrapper).randomBytes(any())

        res.assertRight {
            assertEquals(saltSize, it.size)
        }
    }

    @Test
    fun `given generateNonce is called, then return correct size ByteArray`() {
        val nonceSize = NONCE_SIZE

        `when`(cryptoWrapper.polyNpubBytes()).thenReturn(nonceSize)

        val res = crypto.generateNonce()

        verify(cryptoWrapper).randomBytes(any())

        res.assertRight {
            assertEquals(nonceSize, it.size)
        }
    }

    @Test
    fun `given hash is requested, when push message is invalid, then propagate HashingFailed error`() {
        `when`(cryptoWrapper.generatePwhashMessagePart(any(), any(), any())).thenReturn(INVALID_MESSAGE_PART)

        val res = crypto.hashWithMessagePart(String.empty(), byteArrayOf())

        res.assertLeft {
            assertEquals(it, HashingFailed)
        }
    }

    @Test
    fun `given hash is requested, when push message is valid, then assert key is correct size`() {
        val polyKeyBytes = POLY_KEY_BYTES

        `when`(cryptoWrapper.aedPolyKeyBytes()).thenReturn(polyKeyBytes)
        `when`(cryptoWrapper.generatePwhashMessagePart(any(), any(), any())).thenReturn(0)

        val res = crypto.hashWithMessagePart(String.empty(), byteArrayOf())

        res.assertRight {
            assertEquals(it.size, polyKeyBytes)
        }
    }

    @Test
    fun `given opsLimit is requested, then return limit through wrapper`() {
        val opsLimit = OPS_LIMIT

        `when`(cryptoWrapper.opsLimitInteractive()).thenReturn(opsLimit)

        val limit = crypto.opsLimit()

        assertEquals(limit, opsLimit)

    }

    @Test
    fun `given memLimit is requested, then return limit through wrapper`() {
        val memLimit = MEM_LIMIT

        `when`(cryptoWrapper.memLimitInteractive()).thenReturn(memLimit)

        val limit = crypto.memLimit()

        assertEquals(limit, memLimit)
    }

    @Test
    fun `given a bytes length is requested, then return a bytes length through wrapper`() {
        val aBytesLength = A_BYTES_LENGTH

        `when`(cryptoWrapper.polyABytes()).thenReturn(aBytesLength)

        val aBytes = crypto.aBytesLength()

        assertEquals(aBytes, aBytesLength)
    }

    @Test
    fun `given expected encrypted bytes is called, then return expected bytes`() {
        val bytes = EXPECTED_HASH_SIZE

        `when`(cryptoWrapper.aedPolyKeyBytes()).thenReturn(bytes)

        val pullBytes = crypto.encryptExpectedKeyBytes()

        assertEquals(pullBytes, bytes)
    }

    @Test
    fun `given hash size is same as expected size, when checkExpectedKeySize is called, then propagate success`() {
        val bytes = EXPECTED_HASH_SIZE
        val expectedBytes = EXPECTED_HASH_SIZE

        val res = crypto.checkExpectedKeySize(bytes, expectedBytes, false)

        res.assertRight()
    }

    @Test
    fun `given hash size is not the same as expected size, when checkExpectedKeySize is called, then propagate HashWrongSize error`() {
        val bytes = 62
        val expectedBytes = EXPECTED_HASH_SIZE

        val res = crypto.checkExpectedKeySize(bytes, expectedBytes, false)

        res.assertLeft {
            assertEquals(it, HashWrongSize)
        }
    }

    companion object {
        private const val INVALID_MESSAGE_PART = 12
        private const val NONCE_SIZE = 24
        private const val SALT_SIZE = 32
        private const val EXPECTED_HASH_SIZE = 64
        private const val A_BYTES_LENGTH = 31
        private const val MEM_LIMIT = 22
        private const val OPS_LIMIT = 22
        private const val POLY_KEY_BYTES = 32
    }

}
