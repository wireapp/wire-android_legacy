package com.waz.zclient.feature.backup.crypto.encryption

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.HashInvalid
import com.waz.zclient.feature.backup.crypto.encryption.error.HashWrongSize
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import com.waz.zclient.framework.functional.assertLeft
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.io.File
import java.util.*
import kotlin.random.Random

class EncryptionHandlerTest : UnitTest() {

    private lateinit var encryptionHandler: EncryptionHandler

    @Mock
    private lateinit var crypto: Crypto

    @Mock
    private lateinit var headerMetaData: CryptoHeaderMetaData

    @Before
    fun setup() {
        encryptionHandler = EncryptionHandler(crypto, headerMetaData)
    }

    @Test
    fun `given a backup file, userId, and password, when salt, nonce, and hash is valid, then write encrypted meta data`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val salt = ByteArray(TEST_KEY_BYTES)
        val nonce = ByteArray(TEST_NONCE_BYTES)
        val hash = ByteArray(ENCRYPTION_HASH_BYTES)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.generateNonce()).thenReturn(Either.Right(nonce))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(headerMetaData.createMetaData(any(), any(), any())).thenReturn(Either.Right(byteArrayOf()))
        `when`(crypto.encryptExpectedKeyBytes()).thenReturn(ENCRYPTION_HASH_BYTES)
        `when`(crypto.checkExpectedKeySize(ENCRYPTION_HASH_BYTES, ENCRYPTION_HASH_BYTES)).thenReturn(Either.Right(Unit))

        encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")

        verify(headerMetaData).createMetaData(eq(salt), eq(hash), eq(nonce))
    }

    @Test
    fun `given a backup file, userId, and password, when the salt fails to be generated, then fail encryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()

        `when`(crypto.generateSalt()).thenReturn(Either.Left(FakeSodiumLibError))

         encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")
             .assertLeft { it shouldEqual FakeSodiumLibError }
    }

    @Test
    fun `given a backup file, userId, and password, when the nonce fails to be generated, then fail encryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val salt = ByteArray(TEST_KEY_BYTES)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.generateNonce()).thenReturn(Either.Left(FakeSodiumLibError))

        encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")
            .assertLeft { it shouldEqual FakeSodiumLibError }
    }

    @Test
    fun `given a backup file, userId, and password, when hashing the password fails, then fail encryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val salt = ByteArray(TEST_KEY_BYTES)
        val nonce = ByteArray(TEST_NONCE_BYTES)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.generateNonce()).thenReturn(Either.Right(nonce))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Left(HashingFailed))

        encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")
            .assertLeft { it shouldEqual HashingFailed }
    }

    @Test
    fun `given a backup file, userId, and password, when metadata creation fails, then fail encryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val salt = ByteArray(TEST_KEY_BYTES)
        val hash = ByteArray(ENCRYPTION_HASH_BYTES)
        val nonce = ByteArray(TEST_NONCE_BYTES)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.generateNonce()).thenReturn(Either.Right(nonce))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(headerMetaData.createMetaData(salt, hash, nonce)).thenReturn(Either.Left(HashInvalid))

        encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")
            .assertLeft { it shouldEqual HashInvalid }
    }

    @Test
    fun `given a backup file, userId, and password, when the key size check fails, then fail encryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val salt = ByteArray(TEST_KEY_BYTES)
        val nonce = ByteArray(TEST_NONCE_BYTES)
        val wrongHashBytesSize = ENCRYPTION_HASH_BYTES + 1
        val hash = ByteArray(wrongHashBytesSize)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.generateNonce()).thenReturn(Either.Right(nonce))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(headerMetaData.createMetaData(any(), any(), any())).thenReturn(Either.Right(byteArrayOf()))
        `when`(crypto.encryptExpectedKeyBytes()).thenReturn(ENCRYPTION_HASH_BYTES)
        `when`(crypto.checkExpectedKeySize(wrongHashBytesSize, ENCRYPTION_HASH_BYTES)).thenReturn(Either.Left(HashWrongSize))

        encryptionHandler.encryptBackup(backupFile, userId, password, backupFile.name + "_encrypted")
            .assertLeft { it shouldEqual HashWrongSize }
    }

    private fun generateText(length: Int): String = java.util.Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

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
        return "EncryptionHandlerTest_${System.currentTimeMillis()}.txt"
    }

    companion object {
        private const val TEST_KEY_BYTES = 256
        private const val ENCRYPTION_HASH_BYTES = 52
        private const val TEST_NONCE_BYTES = 24

        object FakeSodiumLibError : FeatureFailure()
    }
}
