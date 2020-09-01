package com.waz.zclient.feature.backup.crypto.encryption

import com.waz.model.UserId
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
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
    fun `given a backup file, userId, and password, when salt is valid and hash is valid, then write encrypted meta data`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)

        val userId = UserId.apply()
        val salt = ByteArray(TEST_KEY_BYTES)
        val hash = ByteArray(ENCRYPTION_HASH_BYTES)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.encryptExpectedKeyBytes()).thenReturn(ENCRYPTION_HASH_BYTES)
        `when`(crypto.initEncryptState(any(), any())).thenReturn(Either.Right(byteArrayOf()))
        `when`(crypto.checkExpectedKeySize(ENCRYPTION_HASH_BYTES, ENCRYPTION_HASH_BYTES)).thenReturn(Either.Right(Unit))
        `when`(headerMetaData.writeMetaData(any(), any())).thenReturn(Either.Right(byteArrayOf()))

        encryptionHandler.encryptBackup(backupFile, userId, password)

        verify(headerMetaData).writeMetaData(eq(salt), eq(hash))

    }

    @Test
    fun `given backup file, user id, password and encrypted meta data, when encryption state is initialised, then generated encrypted message`() {
        val tempDir = createTempDir()
        val backupFile = createTextFile(tempDir)
        val password = generateText(8)
        val userId = UserId.apply()

        val salt = ByteArray(TEST_KEY_BYTES)
        val streamHeader = TEST_KEY_BYTES
        val hash = ByteArray(ENCRYPTION_HASH_BYTES)
        val cipherText = ByteArray(backupFile.readBytes().size + 15)

        `when`(crypto.generateSalt()).thenReturn(Either.Right(salt))
        `when`(crypto.hashWithMessagePart(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.streamHeaderLength()).thenReturn(streamHeader)
        `when`(crypto.aBytesLength()).thenReturn(15)
        `when`(crypto.encryptExpectedKeyBytes()).thenReturn(ENCRYPTION_HASH_BYTES)
        `when`(crypto.initEncryptState(any(), any())).thenReturn(Either.Right(hash))
        `when`(crypto.checkExpectedKeySize(ENCRYPTION_HASH_BYTES, ENCRYPTION_HASH_BYTES)).thenReturn(Either.Right(Unit))
        `when`(headerMetaData.writeMetaData(salt, hash)).thenReturn(Either.Right(hash))

        encryptionHandler.encryptBackup(backupFile, userId, password)

        verify(crypto).initEncryptState(any(), any())
        verify(crypto).generatePushMessagePart(eq(hash), eq(cipherText), eq(backupFile.readBytes()))

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
        return "EncryptionHandlerTest_${System.currentTimeMillis()}.txt"
    }

    companion object {
        private const val TEST_KEY_BYTES = 256
        private const val ENCRYPTION_HASH_BYTES = 52
    }
}
