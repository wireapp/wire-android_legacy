package com.waz.zclient.feature.backup.crypto.decryption

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.DecryptionFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.HashesDoNotMatch
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import com.waz.zclient.feature.backup.crypto.header.EncryptedBackupHeader
import com.waz.zclient.framework.functional.assertLeft
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.UUID
import java.util.Base64
import kotlin.random.Random

class DecryptionHandlerTest : UnitTest() {

    private lateinit var decryptionHandler: DecryptionHandler

    @Mock
    private lateinit var crypto: Crypto

    @Mock
    private lateinit var headerMetaData: CryptoHeaderMetaData

    @Before
    fun setup() {
        `when`(crypto.loadLibrary).thenReturn(Either.Right(Unit))
        decryptionHandler = DecryptionHandler(crypto, headerMetaData)
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Storage @TSFI.UserInterface @S0.1 @S0.2
    fun `given backup file, userId, password, when meta data fails, then propagate meta data error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()

        `when`(headerMetaData.readMetadata(backupFile)).thenReturn(Either.Right(EncryptedBackupHeader.EMPTY))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(byteArrayOf(2)))

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.assertLeft {
            assertEquals(it, HashesDoNotMatch)
        }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Storage @TSFI.UserInterface @S0.1 @S0.2
    fun `given backup file, userId, password, when meta data and hash do not match, then propagate hash error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val hash = ByteArray(TEST_KEY_BYTES)

        `when`(headerMetaData.readMetadata(backupFile)).thenReturn(Either.Right(EncryptedBackupHeader.EMPTY))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.assertLeft {
            assertEquals(it, HashesDoNotMatch)
        }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Storage @TSFI.UserInterface @S0.1 @S0.2
    fun `given backup file, userId, password, when decrypted message part is invalid, then propagate error`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UUID.randomUUID().toString()
        val hash = ByteArray(DECRYPTION_HASH_BYTES)
        val salt = ByteArray(TEST_KEY_BYTES)
        val nonce = ByteArray(NONCE_BYTES)
        val metaData = EncryptedBackupHeader(salt = salt, uuidHash = hash, nonce = nonce)

        `when`(crypto.decryptExpectedKeyBytes()).thenReturn(DECRYPTION_HASH_BYTES)
        `when`(headerMetaData.readMetadata(backupFile)).thenReturn(Either.Right(metaData))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.checkExpectedKeySize(DECRYPTION_HASH_BYTES, DECRYPTION_HASH_BYTES)).thenReturn(Either.Right(Unit))

        `when`(crypto.decrypt(any(), any(), any(), any())).thenReturn(-1)

        val res = decryptionHandler.decryptBackup(backupFile, userId, password)

        res.assertLeft {
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
        private const val TEST_KEY_BYTES = 256
        private const val DECRYPTION_HASH_BYTES = 52
        private const val NONCE_BYTES = 24
    }
}
