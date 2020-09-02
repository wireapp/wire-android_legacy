package com.waz.zclient.feature.backup.crypto.decryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.describe
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.logging.Logger.Companion.verbose
import com.waz.zclient.feature.backup.crypto.Crypto
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
        verbose(TAG, "decryptBackup")
        loadCryptoLibrary()
        return cryptoHeaderMetaData.readMetadata(backupFile).flatMap { metaData ->
            crypto.hashWithMessagePart(userId.str(), metaData.salt).flatMap { hash ->
                when (hash.contentEquals(metaData.uuidHash)) {
                    true -> decryptBackupFile(password, backupFile, metaData.salt)
                    false -> Either.Left(HashesDoNotMatch)
                }
            }
        }
    }

    private fun decryptBackupFile(password: String, backupFile: File, salt: ByteArray): Either<Failure, File> {
        val backupLength = backupFile.length() - TOTAL_HEADER_LENGTH
        val cipherText = ByteArray(backupLength.toInt())
        backupFile.inputStream().buffered().apply {
            skip(TOTAL_HEADER_LENGTH.toLong())
            read(cipherText)
        }

        verbose(TAG, "CRY cipher text: ${cipherText.describe()}")

        return decryptWithHash(cipherText, password, salt).map { decryptedBackupBytes ->
            verbose(TAG, "CRY decrypted bytes: ${decryptedBackupBytes.describe()}")
            File.createTempFile("wire_backup", ".zip").apply { writeBytes(decryptedBackupBytes) }
        }
    }

    private fun decryptWithHash(cipherText: ByteArray, password: String, salt: ByteArray): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(password, salt).flatMap { key ->
            verbose(TAG, "CRY key: ${key.describe()}")
            crypto.checkExpectedKeySize(key.size, crypto.decryptExpectedKeyBytes()).flatMap {
                decrypt(cipherText, key)
            }
        }

    private fun decrypt(cipherText: ByteArray, key: ByteArray): Either<Failure, ByteArray> {
        val decrypted = ByteArray(cipherText.size - crypto.aBytesLength())
        verbose(TAG, "CRY decrypt, cipherText: ${cipherText.describe()}")
        return when (crypto.decrypt(decrypted, cipherText, key)) {
            0 -> Either.Right(decrypted)
            else -> Either.Left(DecryptionFailed)
        }
    }

    private fun loadCryptoLibrary() = crypto.loadLibrary

    companion object {
        private const val TAG = "DescryptionHandler"
    }
}
