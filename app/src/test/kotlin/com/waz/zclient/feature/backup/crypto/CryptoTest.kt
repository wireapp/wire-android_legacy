package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.crypto.encryption.error.EncryptionInitialisationError
import com.waz.zclient.feature.backup.crypto.encryption.error.HashWrongSize
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidHeaderLength
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidKeyLength
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
    fun `given encryption state is initialised, when header length is invalid, then propagate InvalidHeaderLength`() {
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(INVALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initEncryptState(byteArrayOf(), headerBytes)

        res.assertLeft {
            assertEquals(it, InvalidHeaderLength)
        }
    }

    @Test
    fun `given encryption state is initialised, when key length is invalid, then propagate InvalidKeyLength`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(INVALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initEncryptState(keyBytes, byteArrayOf())

        res.assertLeft {
            assertEquals(it, InvalidKeyLength)
        }
    }

    @Test
    fun `given encryption state is initialised, when initialisation fails, then propagate EncryptionInitialisationError`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.initPush(any(), any(), any())).thenReturn(INVALID_INITIALISATION)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initEncryptState(keyBytes, headerBytes)

        res.assertLeft {
            assertEquals(it, EncryptionInitialisationError)
        }
    }

    @Test
    fun `given encryption state is initialised, when initialisation succeeds, then assert state is correct`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.initPush(any(), any(), any())).thenReturn(0)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initEncryptState(keyBytes, headerBytes)

        res.assertRight {
            assertEquals(it.size, STATE_BYTE_ARRAY_SIZE)
        }
    }

    @Test
    fun `given decryption state is initialised, when header length is invalid, then propagate InvalidHeaderLength`() {
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(INVALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initDecryptState(byteArrayOf(), headerBytes)

        res.assertLeft {
            assertEquals(it, InvalidHeaderLength)
        }
    }

    @Test
    fun `given decryption state is initialised, when key length is invalid, then propagate InvalidKeyLength`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(INVALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initDecryptState(keyBytes, byteArrayOf())

        res.assertLeft {
            assertEquals(it, InvalidKeyLength)
        }
    }

    @Test
    fun `given decryption state is initialised, when initialisation fails, then propagate EncryptionInitialisationError`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.initPull(any(), any(), any())).thenReturn(INVALID_INITIALISATION)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initDecryptState(keyBytes, headerBytes)

        res.assertLeft {
            assertEquals(it, EncryptionInitialisationError)
        }
    }

    @Test
    fun `given decryption state is initialised, when initialisation succeeds, then assert state is correct`() {
        val keyBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        val headerBytes = ByteArray(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.initPull(any(), any(), any())).thenReturn(0)
        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)
        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(VALID_INITIALISATION_BYTES_LENGTH)

        val res = crypto.initDecryptState(keyBytes, headerBytes)

        res.assertRight {
            assertEquals(it.size, STATE_BYTE_ARRAY_SIZE)
        }
    }

    @Test
    fun `given generateSalt is called, then return correct size ByteArray`() {
        val saltSize = 32

        `when`(cryptoWrapper.pWhashSaltBytes()).thenReturn(saltSize)

        val res = crypto.generateSalt()

        verify(cryptoWrapper).randomBytes(any())

        res.assertRight {
            assertEquals(it.size, saltSize)
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
        val polyKeyBytes = 32

        `when`(cryptoWrapper.aedPolyKeyBytes()).thenReturn(polyKeyBytes)
        `when`(cryptoWrapper.generatePwhashMessagePart(any(), any(), any())).thenReturn(0)

        val res = crypto.hashWithMessagePart(String.empty(), byteArrayOf())

        res.assertRight {
            assertEquals(it.size, polyKeyBytes)
        }
    }

    @Test
    fun `given opsLimit is requested, then return limit through wrapper`() {
        val opsLimit = 22

        `when`(cryptoWrapper.opsLimitInteractive()).thenReturn(opsLimit)

        val limit = crypto.opsLimit()

        assertEquals(limit, opsLimit)

    }

    @Test
    fun `given memLimit is requested, then return limit through wrapper`() {
        val memLimit = 22

        `when`(cryptoWrapper.memLimitInteractive()).thenReturn(memLimit)

        val limit = crypto.memLimit()

        assertEquals(limit, memLimit)
    }

    @Test
    fun `given header length is requested, then return header length through wrapper`() {
        val headerLength = 31

        `when`(cryptoWrapper.polyHeaderBytes()).thenReturn(headerLength)

        val header = crypto.streamHeaderLength()

        assertEquals(header, headerLength)
    }

    @Test
    fun `given a bytes length is requested, then return a bytes length through wrapper`() {
        val aBytesLength = 31

        `when`(cryptoWrapper.polyABytes()).thenReturn(aBytesLength)

        val aBytes = crypto.aBytesLength()

        assertEquals(aBytes, aBytesLength)
    }

    @Test
    fun `given generate push message part is called, then return push message part through wrapper`() {
        val bytes = 64

        `when`(cryptoWrapper.generatePushMessagePart(any(), any(), any())).thenReturn(bytes)

        val pushBytes = crypto.generatePushMessagePart(byteArrayOf(), byteArrayOf(), byteArrayOf())

        assertEquals(pushBytes, bytes)
    }

    @Test
    fun `given generate pull message part is called, then return pull message part through wrapper`() {
        val bytes = 64

        `when`(cryptoWrapper.generatePullMessagePart(any(), any(), any())).thenReturn(bytes)

        val pullBytes = crypto.generatePullMessagePart(byteArrayOf(), byteArrayOf(), byteArrayOf())

        assertEquals(pullBytes, bytes)
    }

    @Test
    fun `given expected encrypted bytes is called, then return expected bytes`() {
        val bytes = 64

        `when`(cryptoWrapper.aedPolyKeyBytes()).thenReturn(bytes)

        val pullBytes = crypto.encryptExpectedKeyBytes()

        assertEquals(pullBytes, bytes)
    }

    @Test
    fun `given expected decrypted bytes is called, then return expected bytes`() {
        val bytes = 64

        `when`(cryptoWrapper.secretStreamPolyKeyBytes()).thenReturn(bytes)

        val pullBytes = crypto.decryptExpectedKeyBytes()

        assertEquals(pullBytes, bytes)
    }

    @Test
    fun `given hash size is same as expected size, then propagate success`() {
        val bytes = 64
        val expectedBytes = 64

        val res = crypto.checkExpectedKeySize(bytes, expectedBytes, false)

        res.assertRight()
    }

    @Test
    fun `given hash size is not the same as expected size, then propagate HashWrongSize error`() {
        val bytes = 64
        val expectedBytes = 62

        val res = crypto.checkExpectedKeySize(bytes, expectedBytes, false)

        res.assertLeft {
            assertEquals(it, HashWrongSize)
        }
    }

    companion object {
        private const val VALID_INITIALISATION_BYTES_LENGTH = 32
        private const val INVALID_INITIALISATION_BYTES_LENGTH = 64
        private const val INVALID_INITIALISATION = 1
        private const val INVALID_MESSAGE_PART = 12
        private const val STATE_BYTE_ARRAY_SIZE = 52
    }

}
