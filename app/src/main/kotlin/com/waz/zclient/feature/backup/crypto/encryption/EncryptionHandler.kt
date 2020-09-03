package com.waz.zclient.feature.backup.crypto.encryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.extension.describe
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.logging.Logger.Companion.verbose
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.EncryptionFailed
import com.waz.zclient.feature.backup.crypto.header.CryptoHeaderMetaData
import java.io.File
import java.io.IOException

class EncryptionHandler(
    private val crypto: Crypto,
    private val cryptoHeaderMetaData: CryptoHeaderMetaData
) {
    fun encryptBackup(backupFile: File, userId: UserId, password: String, targetFileName: String): Either<Failure, File> =
        try {
            loadCryptoLibrary()
            encryptBackupFile(backupFile, userId, password).map {
                val meta = it.first
                val encryptedBytes = it.second
                File(backupFile.parentFile, targetFileName).apply {
                    writeBytes(meta)
                    appendBytes(encryptedBytes)
                }
            }
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }

    private fun encryptBackupFile(backupFile: File, userId: UserId, password: String) =
        crypto.generateSalt().flatMap { salt ->
            crypto.generateNonce().flatMap { nonce ->
                createMetaData(salt, nonce, userId).flatMap { meta ->
                    val backupBytes = backupFile.readBytes()
                    encryptWithHash(backupBytes, password, salt, nonce).map { encryptedBytes -> Pair(meta, encryptedBytes) }
                }
            }
        }

    private fun encryptWithHash(backupBytes: ByteArray, password: String, salt: ByteArray, nonce: ByteArray): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(password, salt).flatMap { hash ->
            crypto.checkExpectedKeySize(hash.size, crypto.encryptExpectedKeyBytes()).flatMap {
                encrypt(backupBytes, hash, nonce)
            }
        }

    private fun encrypt(backupBytes: ByteArray, hash: ByteArray, nonce: ByteArray): Either<Failure, ByteArray> {
        val cipherText = ByteArray(backupBytes.size + crypto.aBytesLength())
        return when (crypto.encrypt(cipherText, backupBytes, hash, nonce)) {
            0 -> Either.Right(cipherText)
            else -> Either.Left(EncryptionFailed)
        }
    }

    // This method returns the metadata in the format described here:
    // https://wearezeta.atlassian.net/wiki/spaces/PROD/pages/59965445/Exporting+history+v1
    private fun createMetaData(salt: ByteArray, nonce: ByteArray, userId: UserId): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(userId.str(), salt).flatMap { key ->
            cryptoHeaderMetaData.createMetaData(salt, key, nonce)
        }

    private fun loadCryptoLibrary() = crypto.loadLibrary

    companion object {
        const val TAG = "EncryptionHandler"
    }
}
