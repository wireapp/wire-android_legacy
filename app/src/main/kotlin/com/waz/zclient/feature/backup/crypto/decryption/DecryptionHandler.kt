package com.waz.zclient.feature.backup.crypto.decryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.crypto.encryption.error.DecryptionFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.HashesDoNotMatch
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import com.waz.zclient.feature.backup.crypto.header.TOTAL_HEADER_LENGTH
import java.io.File

class DecryptionHandler(
    private val crypto: Crypto,
    private val cryptoHeaderMetaData: CryptoHeaderMetaData
) {
    fun decryptBackup(backupFile: File, userId: UserId, password: String): Either<Failure, File> {
        loadCryptoLibrary()
        return cryptoHeaderMetaData.readEncryptedMetadata(backupFile).flatMap { metaData ->
            crypto.hash(userId.str(), metaData.salt).flatMap { hash ->
                when (hash.contentEquals(metaData.uuidHash)) {
                    true -> decryptBackupFile(password, backupFile, metaData.salt)
                    false -> Either.Left(HashesDoNotMatch)
                }
            }
        }
    }

    private fun decryptBackupFile(password: String, backupFile: File, salt: ByteArray): Either<Failure, File> {
        val encryptedBackupBytes = ByteArray(TOTAL_HEADER_LENGTH)
        backupFile.inputStream().buffered().read(encryptedBackupBytes)
        return decryptWithHash(encryptedBackupBytes, password, salt).map { decryptedBackupBytes ->
            File.createTempFile("wire_backup", ".zip").apply { writeBytes(decryptedBackupBytes) }
        }
    }

    private fun decryptWithHash(input: ByteArray, password: String, salt: ByteArray): Either<Failure, ByteArray> =
        crypto.hash(password, salt).flatMap { key ->
            checkExpectedKeySize(key.size, crypto.decryptExpectedKeyBytes())
            decryptAndCipher(input, key)
        }

    private fun decryptAndCipher(input: ByteArray, key: ByteArray): Either<Failure, ByteArray> {
        val header = input.take(crypto.streamHeaderLength()).toByteArray()
        return crypto.initDecryptState(key, header)
            .flatMap { state ->
                val cipherText = input.drop(crypto.streamHeaderLength()).toByteArray()
                val decrypted = ByteArray(cipherText.size + crypto.aBytesLength())
                when (crypto.generatePullMessagePart(state, decrypted, cipherText)) {
                    0 -> Either.Right(decrypted)
                    else -> Either.Left(DecryptionFailed)
                }
            }
    }

    private fun checkExpectedKeySize(size: Int, expectedKeySize: Int) {
        if (size != expectedKeySize) {
            Logger.verbose(EncryptionHandler.TAG, "Key length invalid: $size did not match $expectedKeySize")
        }
    }

    private fun loadCryptoLibrary() = crypto.loadLibrary

    companion object {
        private const val TAG = "DecryptionHandler"
    }
}
