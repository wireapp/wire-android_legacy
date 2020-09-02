package com.waz.zclient.feature.backup.crypto.header

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.describe
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.feature.backup.crypto.Crypto
import com.waz.zclient.feature.backup.crypto.encryption.error.HashInvalid
import com.waz.zclient.feature.backup.crypto.encryption.error.UnableToReadMetaData
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

private const val SALT_LENGTH = 16
private const val ANDROID_MAGIC_NUMBER_LENGTH = 4
private const val TAG = "EncryptionHeader"
internal const val CURRENT_VERSION: Short = 2

const val UUID_HASH_LENGTH = 32
const val TOTAL_HEADER_LENGTH = ANDROID_MAGIC_NUMBER_LENGTH + 1 + 2 + SALT_LENGTH + UUID_HASH_LENGTH + 4 + 4

class CryptoHeaderMetaData(
    private val crypto: Crypto,
    private val encryptionHeaderMapper: EncryptionHeaderMapper
) {
    fun readMetadata(encryptedBackup: File): Either<Failure, EncryptedBackupHeader> =
        if (encryptedBackup.length() > TOTAL_HEADER_LENGTH) {
            val encryptedMetadataBytes = ByteArray(TOTAL_HEADER_LENGTH)
            encryptedBackup.inputStream().buffered().read(encryptedMetadataBytes)
            val mappedHeader = encryptionHeaderMapper.fromByteArray(encryptedMetadataBytes)
            mappedHeader?.let {
                Either.Right(it)
            } ?: Either.Left(UnableToReadMetaData)
        } else {
            Either.Left(UnableToReadMetaData)
        }

    fun writeMetaData(salt: ByteArray, hash: ByteArray): Either<Failure, ByteArray> =
        hash.size.takeIf { it == UUID_HASH_LENGTH }?.let {
            val header = EncryptedBackupHeader(CURRENT_VERSION, salt, hash, crypto.opsLimit(), crypto.memLimit())
            Either.Right(encryptionHeaderMapper.toByteArray(header))
        } ?: Either.Left(HashInvalid)
}

class EncryptionHeaderMapper {

    fun toByteArray(header: EncryptedBackupHeader): ByteArray =
        ByteBuffer.allocate(TOTAL_HEADER_LENGTH).apply {
            Logger.verbose(TAG, "android magic number: ${ANDROID_MAGIC_NUMBER.contentToString()}")
            put(ANDROID_MAGIC_NUMBER)
            put(0.toByte())
            putShort(header.version)
            put(header.salt)
            put(header.uuidHash)
            putInt(header.opsLimit)
            putInt(header.memLimit)
        }.array().also {
            Logger.verbose(TAG, it.describe())
        }

    internal fun fromByteArray(bytes: ByteArray): EncryptedBackupHeader? =
        if (bytes.size == TOTAL_HEADER_LENGTH) {
            val buffer = ByteBuffer.wrap(bytes)
            val magicNumber = ByteArray(ANDROID_MAGIC_NUMBER_LENGTH)
            buffer.get(magicNumber)
            if (magicNumber.contentEquals(ANDROID_MAGIC_NUMBER)) {
                buffer.get() //skip null byte
                val version = buffer.short
                if (version == CURRENT_VERSION) {
                    val salt = ByteArray(SALT_LENGTH)
                    buffer.get(salt)
                    val uuidHash = ByteArray(UUID_HASH_LENGTH)
                    buffer.get(uuidHash)
                    val opslimit = buffer.int
                    val memlimit = buffer.int
                    EncryptedBackupHeader(CURRENT_VERSION, salt, uuidHash, opslimit, memlimit)
                } else {
                    Logger.error(TAG, "Unsupported backup version: $version (should be $CURRENT_VERSION)")
                    null
                }
            } else {
                Logger.error(TAG, "archive has incorrect magic number: ${magicNumber.contentToString()} (should be: ${ANDROID_MAGIC_NUMBER.contentToString()})")
                null
            }
        } else {
            Logger.error(TAG, "Invalid header length: ${bytes.size} (should be: $TOTAL_HEADER_LENGTH)")
            null
        }

    companion object {
        private val ANDROID_MAGIC_NUMBER = "WBUA".toCharArray().map { it.toByte() }.toByteArray()
    }
}

data class EncryptedBackupHeader(
    val version: Short = CURRENT_VERSION,
    val salt: ByteArray,
    val uuidHash: ByteArray,
    val opsLimit: Int = 0,
    val memLimit: Int = 0
) {
    companion object {
        val EMPTY = EncryptedBackupHeader(CURRENT_VERSION, byteArrayOf(), byteArrayOf(), 0, 0)
    }
}
