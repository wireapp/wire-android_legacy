package com.waz.zclient.feature.backup.crypto.decryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.logging.Logger.Companion.error
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.DecryptionFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.HashesDoNotMatch
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import com.waz.zclient.feature.backup.crypto.header.TOTAL_HEADER_LENGTH
import java.io.File
import java.io.IOException

class DecryptionHandler(
    private val crypto: Crypto,
    private val cryptoHeaderMetaData: CryptoHeaderMetaData
) {
    fun decryptBackup(backupFile: File, userId: UserId, password: String): Either<Failure, File> =
        loadCryptoLibrary().flatMap {
            cryptoHeaderMetaData.readMetadata(backupFile)
        }.flatMap { metaData ->
            crypto.hashWithMessagePart(userId.str(), metaData.salt).flatMap { hash ->
                when (hash.contentEquals(metaData.uuidHash)) {
                    true -> decryptBackupFile(password, backupFile, metaData.salt, metaData.nonce)
                    false -> Either.Left(HashesDoNotMatch)
                }
            }
        }

    private fun decryptBackupFile(password: String, backupFile: File, salt: ByteArray, nonce: ByteArray): Either<Failure, File> =
        readCipherText(backupFile).flatMap { cipherText ->
            decryptWithHash(cipherText, password, salt, nonce)
        }.map { decryptedBackupBytes ->
            File.createTempFile(TMP_FILE_NAME, TMP_FILE_EXTENSION).apply {
                writeBytes(decryptedBackupBytes)
            }
        }

    private fun readCipherText(backupFile: File): Either<Failure, ByteArray> =
        if (TOTAL_HEADER_LENGTH >= backupFile.length()) Either.Left(DecryptionFailed)
        else {
            try {
                val cipherText = ByteArray(backupFile.length().toInt() - TOTAL_HEADER_LENGTH).also {
                    backupFile.inputStream().buffered().apply {
                        skip(TOTAL_HEADER_LENGTH.toLong())
                        read(it)
                    }
                }
                Either.Right(cipherText)
            } catch (ex: IOException) {
                error(TAG, "IO error when reading the backup file: ${ex.message}")
                Either.Left(IOFailure(ex))
            }
        }

    private fun decryptWithHash(cipherText: ByteArray, password: String, salt: ByteArray, nonce: ByteArray): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(password, salt).flatMap { key ->
            crypto.checkExpectedKeySize(key.size, crypto.decryptExpectedKeyBytes()).flatMap {
                decrypt(cipherText, key, nonce)
            }
        }

    private fun decrypt(cipherText: ByteArray, key: ByteArray, nonce: ByteArray): Either<Failure, ByteArray> =
        if (crypto.aBytesLength() > cipherText.size) Either.Left(DecryptionFailed)
        else {
            val decrypted = ByteArray(cipherText.size - crypto.aBytesLength())
            when (crypto.decrypt(decrypted, cipherText, key, nonce)) {
                0 -> Either.Right(decrypted)
                else -> Either.Left(DecryptionFailed)
            }
        }

    private fun loadCryptoLibrary() = crypto.loadLibrary

    companion object {
        private const val TAG = "DecryptionHandler"
        private const val TMP_FILE_NAME = "wire_backup"
        private const val TMP_FILE_EXTENSION = ".zip"
    }
}
