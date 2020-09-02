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
import com.waz.zclient.feature.backup.crypto.header.TOTAL_HEADER_LENGTH
import java.io.File
import java.io.IOException

class EncryptionHandler(
    private val crypto: Crypto,
    private val cryptoHeaderMetaData: CryptoHeaderMetaData
) {
    fun encryptBackup(backupFile: File, userId: UserId, password: String): Either<Failure, File> =
        try {
            loadCryptoLibrary()
            crypto.generateSalt().flatMap { salt ->
                writeMetaData(salt, userId).flatMap { meta ->
                    verbose(TAG, "CRY meta: ${meta.describe()}")
                    val backupBytes = backupFile.readBytes().drop(TOTAL_HEADER_LENGTH).toByteArray()
                    verbose(TAG, "CRY backup bytes: ${backupBytes.describe()}")
                    encryptWithHash(backupBytes, password, salt).map { encryptedBytes ->
                        verbose(TAG, "CRY encrypted bytes: ${encryptedBytes.describe()}")
                        return@map File(backupFile.parentFile, backupFile.name + "_encrypted").apply {
                            writeBytes(meta)
                            appendBytes(encryptedBytes)
                        }
                    }
                }
            }
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }

    private fun encryptWithHash(backupBytes: ByteArray, password: String, salt: ByteArray): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(password, salt).flatMap { hash ->
            verbose(TAG, "CRY key: ${hash.describe()}")
            crypto.checkExpectedKeySize(hash.size, crypto.encryptExpectedKeyBytes()).flatMap {
                encrypt(backupBytes, hash)
            }
        }

    private fun encrypt(backupBytes: ByteArray, hash: ByteArray): Either<Failure, ByteArray> {
        val cipherText = ByteArray(backupBytes.size + crypto.aBytesLength())
        return when (crypto.encrypt(cipherText, backupBytes, hash)) {
            0 -> Either.Right(cipherText)
            else -> Either.Left(EncryptionFailed)
        }
    }

    //This method returns the metadata in the format described here:
    //https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history.md
    private fun writeMetaData(salt: ByteArray, userId: UserId): Either<Failure, ByteArray> =
        crypto.hashWithMessagePart(userId.str(), salt).flatMap { hash ->
            cryptoHeaderMetaData.writeMetaData(salt, hash)
        }

    private fun loadCryptoLibrary() = crypto.loadLibrary

    companion object {
        const val TAG = "EncryptionHandler"
    }
}
