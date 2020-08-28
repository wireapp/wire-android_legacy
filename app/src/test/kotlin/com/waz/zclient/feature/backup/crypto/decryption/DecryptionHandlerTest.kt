package com.waz.zclient.feature.backup.crypto.decryption

import com.waz.model.UserId
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.DecryptionFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.DecryptionInitialisationError
import com.waz.zclient.feature.backup.crypto.encryption.error.HashesDoNotMatch
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import com.waz.zclient.feature.backup.crypto.header.EncryptedBackupHeader
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.io.File
import java.util.*
import kotlin.random.Random

class DecryptionHandlerTest : UnitTest() {

    private lateinit var decryptionHandler: DecryptionHandler

    @Mock
    private lateinit var crypto: Crypto

    @Mock
    private lateinit var headerMetaData: CryptoHeaderMetaData

    @Before
    fun setup() {
        decryptionHandler = DecryptionHandler(crypto, headerMetaData)
    }

    @Test
    fun `given backup file, userId, password, when meta data fails, then propagate meta data error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()

        `when`(headerMetaData.readEncryptedMetadata(backupFile)).thenReturn(Either.Right(EncryptedBackupHeader.EMPTY))
        `when`(crypto.hash(any(), any())).thenReturn(Either.Right(byteArrayOf()))
        `when`(crypto.initDecryptState(any(), any())).thenReturn(Either.Right(byteArrayOf()))

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.onFailure {
            assertEquals(it, HashesDoNotMatch)
        }
    }

    @Test
    fun `given backup file, userId, password, when meta data and hash do not match, then propagate hash error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()
        val hash = ByteArray(TEST_KEY_BYTES)

        `when`(headerMetaData.readEncryptedMetadata(backupFile)).thenReturn(Either.Right(EncryptedBackupHeader.EMPTY))
        `when`(crypto.hash(any(), any())).thenReturn(Either.Right(hash))

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.onFailure {
            assertEquals(it, HashesDoNotMatch)
        }
    }

    @Test
    fun `given backup file, userId, password, when decrypt state is null, then propagate error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()
        val hash = ByteArray(DECRYPTION_HASH_BYTES)
        val salt = ByteArray(TEST_KEY_BYTES)
        val metaData = EncryptedBackupHeader(salt = salt, uuidHash = hash)

        `when`(headerMetaData.readEncryptedMetadata(backupFile)).thenReturn(Either.Right(metaData))
        `when`(crypto.hash(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.initDecryptState(any(), any())).thenReturn(Either.Right(byteArrayOf()))
        `when`(crypto.decryptExpectedKeyBytes()).thenReturn(DECRYPTION_HASH_BYTES)

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.onFailure {
            assertEquals(it, DecryptionInitialisationError)
        }
    }

    @Test
    fun `given backup file, userId, password, when meta data and hash do match, then init decryption`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()
        val hash = ByteArray(DECRYPTION_HASH_BYTES)
        val salt = ByteArray(TEST_KEY_BYTES)
        val metaData = EncryptedBackupHeader(salt = salt, uuidHash = hash)
        val header = backupFile.readBytes().take(HEADER_STREAM_LENGTH).toByteArray()

        `when`(crypto.decryptExpectedKeyBytes()).thenReturn(DECRYPTION_HASH_BYTES)
        `when`(crypto.streamHeaderLength()).thenReturn(HEADER_STREAM_LENGTH)
        `when`(headerMetaData.readEncryptedMetadata(backupFile)).thenReturn(Either.Right(metaData))
        `when`(crypto.hash(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.initDecryptState(any(), any())).thenReturn(Either.Left(DecryptionInitialisationError))

        decryptionHandler.decryptBackup(backupFile, userId, password)

        verify(crypto).initDecryptState(hash, header)
    }

    @Test
    fun `given backup file, userId, password, when decrypted message part is invalid, then propagate error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()
        val hash = ByteArray(DECRYPTION_HASH_BYTES)
        val salt = ByteArray(TEST_KEY_BYTES)
        val metaData = EncryptedBackupHeader(salt = salt, uuidHash = hash)

        `when`(crypto.decryptExpectedKeyBytes()).thenReturn(DECRYPTION_HASH_BYTES)
        `when`(crypto.streamHeaderLength()).thenReturn(HEADER_STREAM_LENGTH)
        `when`(headerMetaData.readEncryptedMetadata(backupFile)).thenReturn(Either.Right(metaData))
        `when`(crypto.hash(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.initDecryptState(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.generatePullMessagePart(any(), any(), any())).thenReturn(1)

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.onFailure {
            assertEquals(it, DecryptionFailed)
        }

    }

    private fun generateText(length: Int): String = Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

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
        return "DecryptionHandlerTest_${System.currentTimeMillis()}.txt"
    }

    companion object {
        private const val HEADER_STREAM_LENGTH = 32
        private const val TEST_KEY_BYTES = 256
        private const val DECRYPTION_HASH_BYTES = 52

    }
}
