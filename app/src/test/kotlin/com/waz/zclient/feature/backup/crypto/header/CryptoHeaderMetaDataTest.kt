package com.waz.zclient.feature.backup.crypto.header

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.HashInvalid
import com.waz.zclient.feature.backup.crypto.encryption.error.UnableToReadMetaData
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.Base64
import kotlin.random.Random

class CryptoHeaderMetaDataTest : UnitTest() {

    private lateinit var cryptoHeaderMetaData: CryptoHeaderMetaData

    @Mock
    private lateinit var crypto: Crypto

    @Mock
    private lateinit var mapper: EncryptionHeaderMapper

    @Before
    fun setup() {
        cryptoHeaderMetaData = CryptoHeaderMetaData(crypto, mapper)
    }

    @Test
    fun `given readEncryptedMetaData is called, when encrypted backup length is less than total header length,then return UnableToReadMetaData error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir, INVALID_HEADER_LENGTH)

        val result = cryptoHeaderMetaData.readMetadata(backupFile)

        result.onFailure {
            assertEquals(it, UnableToReadMetaData)
        }
    }

    @Test
    fun `given readEncryptedMetaData is called,, when encrypted backup length is more than total header length, when mapped bytes is null, then return UnableToReadMetaData error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir, VALID_HEADER_LENGTH)

        `when`(mapper.fromByteArray(any())).thenReturn(null)

        val result = cryptoHeaderMetaData.readMetadata(backupFile)

        result.onFailure {
            assertEquals(it, UnableToReadMetaData)
        }
    }

    @Test
    fun `given readEncryptedMetaData is called, when encrypted backup length is more than total header length and mapped bytes is valid, then return mapped bytes`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir, VALID_HEADER_LENGTH)
        val encryptedHeader = EncryptedBackupHeader.EMPTY

        `when`(mapper.fromByteArray(any())).thenReturn(encryptedHeader)

        val result = cryptoHeaderMetaData.readMetadata(backupFile)

        result.onSuccess {
            assertEquals(it, encryptedHeader)
        }
    }

    @Test
    fun `given writeEncryptedMetaData is called, when hash is valid, then return success`() {
        val salt = byteArrayOf()
        val hash = ByteArray(VALID_HASH_LENGTH)
        val nonce = ByteArray(NONCE_LENGTH)

        val result = cryptoHeaderMetaData.createMetaData(salt, hash, nonce)

        assertTrue(result.isRight)
    }

    @Test
    fun `given writeEncryptedMetaData is called, when hash is invalid, then return HashInvalid error`() {
        val salt = byteArrayOf()
        val hash = ByteArray(INVALID_HASH_LENGTH)
        val nonce = ByteArray(NONCE_LENGTH)

        val result = cryptoHeaderMetaData.createMetaData(salt, hash, nonce)

        result.onFailure {
            assertEquals(it, HashInvalid)
        }
    }

    private fun createTempDir(): File = File.createTempFile("temp", System.currentTimeMillis().toString()).apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private fun createTextFile(dir: File, length: Int = 100): File =
        File(dir, uniqueTextFileName()).apply {
            bufferedWriter().use { it.write(generateText(length)) }
        }

    private fun uniqueTextFileName(): String {
        Thread.sleep(1)
        return "EncryptionMetaDataTest_${System.currentTimeMillis()}.txt"
    }

    private fun generateText(length: Int): String = Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

    companion object {
        private const val INVALID_HEADER_LENGTH = 10
        private const val VALID_HEADER_LENGTH = 65

        private const val VALID_HASH_LENGTH = 32
        private const val INVALID_HASH_LENGTH = 21

        private const val NONCE_LENGTH = 24
    }
}
