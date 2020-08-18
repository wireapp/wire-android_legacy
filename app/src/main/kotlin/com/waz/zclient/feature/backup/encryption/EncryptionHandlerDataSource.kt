package com.waz.zclient.feature.backup.encryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.logging.Logger.Companion.error
import com.waz.zclient.core.logging.Logger.Companion.warn
import com.waz.zclient.core.logging.Logger.Companion.verbose
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom

class EncryptionHandlerDataSource : EncryptionHandler {
    override fun encrypt(backupFile: File, userId: UserId, password: String): Either<Failure, File> =
        try {
            loadLibrary

            val salt = generateSalt()

            getMetaDataBytes(salt, userId).flatMap { meta ->
                val backupBytes = backupFile.readBytes()
                encrypt(backupBytes, password, salt).map { encryptedBytes ->
                    val encryptedFile = File(backupFile.parentFile, backupFile.name + "_encrypted").apply {
                        writeBytes(meta)
                        writeBytes(encryptedBytes)
                    }
                    encryptedFile
                }
            }
        } catch (ex: IOException) {
            Left(IOFailure(ex))
        }

    override fun decrypt(backupFile: File, userId: UserId, password: String): Either<Failure, File> {
        loadLibrary

        val metadata = EncryptedBackupHeader.readEncryptedMetadata(backupFile)
        return if (metadata != null) {
            val hash = hash(userId.str(), metadata.salt)
            if (hash != null && hash.contentEquals(metadata.uuidHash)) {
                val encryptedBackupBytes = ByteArray(EncryptedBackupHeader.totalHeaderLength)
                backupFile.inputStream().buffered().read(encryptedBackupBytes)
                decrypt(encryptedBackupBytes, password, metadata.salt).map { decryptedBackupBytes ->
                    File.createTempFile("wire_backup", ".zip").apply { writeBytes(decryptedBackupBytes) }
                }
            } else if (hash != null) {
                Left(EncryptionFailure("Uuid hashes don't match"))
            } else {
                Left(EncryptionFailure("Uuid hashing failed"))
            }
        } else {
            Left(EncryptionFailure("metadata could not be read"))
        }
    }

    private fun generateSalt(): ByteArray {
        val count = Sodium.crypto_pwhash_saltbytes()
        val buffer = ByteArray(count)

        when (loadLibrary) {
            is Right -> Sodium.randombytes(buffer, count)
            is Left -> {
                warn(TAG, "Libsodium failed to generate $count random bytes. Falling back to SecureRandom")
                secureRandom.nextBytes(buffer)
            }
        }

        return buffer
    }

    private fun encrypt(msg: ByteArray, password: String, salt: ByteArray): Either<Failure, ByteArray> {
        val key = hash(password, salt)

        return if (key != null) {
            val expectedKeySize = Sodium.crypto_aead_chacha20poly1305_keybytes()
            if (key.size != expectedKeySize) {
                verbose(TAG, "Key length invalid: ${key.size} did not match $expectedKeySize")
            }

            val header = ByteArray(Sodium.crypto_secretstream_xchacha20poly1305_headerbytes())
            val s = initPush(key, header)
            if (s != null) {
                val cipherText = ByteArray(msg.size + Sodium.crypto_secretstream_xchacha20poly1305_abytes())
                val ret = Sodium.crypto_secretstream_xchacha20poly1305_push(
                    s,
                    cipherText,
                    emptyArray<Int>().toIntArray(),
                    msg,
                    msg.size,
                    emptyArray<Byte>().toByteArray(),
                    0,
                    Sodium.crypto_secretstream_xchacha20poly1305_tag_final().toShort()
                )
                if (ret == 0) {
                    Right(header + cipherText)
                } else {
                    Left(EncryptionFailure("Failed to hash backup"))
                }
            } else {
                Left(EncryptionFailure("Failed to init encrypt"))
            }
        } else {
            Left(EncryptionFailure("Couldn't derive key from password"))
        }
    }

    private fun decrypt(input: ByteArray, password: String, salt: ByteArray): Either<Failure, ByteArray> {
        val key = hash(password, salt)
        return if (key != null) {
            val expectedKeyBytes = Sodium.crypto_secretstream_xchacha20poly1305_keybytes()

            if (key.size != expectedKeyBytes) {
                verbose(TAG, "Key length invalid: ${key.size} did not match $expectedKeyBytes")
            }

            val header = input.take(streamHeaderLength).toByteArray()
            val s = initPull(key, header)
            if (s != null) {
                val cipherText = input.drop(streamHeaderLength).toByteArray()
                val decrypted = ByteArray(cipherText.size + Sodium.crypto_secretstream_xchacha20poly1305_abytes())
                val tag = ByteArray(1)
                val ret: Int = Sodium.crypto_secretstream_xchacha20poly1305_pull(
                    s, decrypted, IntArray(0), tag, cipherText, cipherText.size, ByteArray(0), 0
                )
                if (ret == 0) {
                    Right(decrypted)
                } else {
                    Left(EncryptionFailure("Failed to decrypt backup, got code $ret"))
                }
            } else {
                Left(EncryptionFailure("Failed to init decrypt"))
            }
        } else {
            Left(EncryptionFailure("Couldn't derive key from password"))
        }
    }

    //This method returns the metadata in the format described here:
    //https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history.md
    private fun getMetaDataBytes(salt: ByteArray, userId: UserId): Either<Failure, ByteArray> {
        val uuidHash = hash(userId.str(), salt)
        return if (uuidHash != null && uuidHash.size == EncryptedBackupHeader.uuidHashLength) {
            val header = EncryptedBackupHeader(EncryptedBackupHeader.currentVersion, salt, uuidHash, opsLimit(), memLimit())
            Right(EncryptedBackupHeader.toByteArray(header))
        } else if (uuidHash != null) {
            Left(EncryptionFailure("uuidHash length invalid, expected: ${EncryptedBackupHeader.uuidHashLength}, got: ${uuidHash.size}"))
        } else {
            Left(EncryptionFailure("Failed to hash account id for backup"))
        }
    }

    private fun hash(input: String, salt: ByteArray): ByteArray? {
        val output = ByteArray(Sodium.crypto_secretstream_xchacha20poly1305_keybytes())
        val passBytes = input.toByteArray()
        val ret = Sodium.crypto_pwhash(
            output,
            output.size,
            passBytes,
            passBytes.size,
            salt,
            opsLimit(),
            memLimit(),
            Sodium.crypto_pwhash_alg_default()
        )

        return if (ret == 0) output else null
    }

    private fun initializeState(key: ByteArray, header: ByteArray, init: (ByteArray, ByteArray, ByteArray) -> Int): ByteArray? =
        if (header.size != Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()) {
            error(TAG, "Invalid header length")
            null
        } else if (key.size != Sodium.crypto_secretstream_xchacha20poly1305_keybytes()) {
            error(TAG, "Invalid key length")
            null
        } else {
            val state = ByteArray(stateByteArraySize)
            if (init(state, header, key) != 0) {
                error(TAG, "error whilst initializing push")
                null
            } else {
                state
            }
        }

    private fun initPush(key: ByteArray, header: ByteArray): ByteArray? = initializeState(key, header) {
        s: ByteArray, h: ByteArray, k: ByteArray -> Sodium.crypto_secretstream_xchacha20poly1305_init_push(s, h, k)
    }

    private fun initPull(key: ByteArray, header: ByteArray): ByteArray? = initializeState(key, header) {
        s: ByteArray, h: ByteArray, k: ByteArray -> Sodium.crypto_secretstream_xchacha20poly1305_init_pull(s, h, k)
    }

    companion object {
        data class EncryptionFailure(val msg: String) : FeatureFailure()

        const val TAG = "EncryptionHandler"

        //Got this magic number from https://github.com/joshjdevl/libsodium-jni/blob/master/src/test/java/org/libsodium/jni/crypto/SecretStreamTest.java#L48
        private const val stateByteArraySize = 52

        private val secureRandom: SecureRandom by lazy { SecureRandom() }

        private val streamHeaderLength = Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()

        private val loadLibrary: Either<Failure, Unit> by lazy {
            try {
                NaCl.sodium() // dynamically load the libsodium library
                System.loadLibrary("sodium")
                System.loadLibrary("randombytes")
                Right(Unit)
            } catch (ex: UnsatisfiedLinkError) {
                Left(GenericUseCaseError(ex))
            }
        }

        private fun opsLimit(): Int = Sodium.crypto_pwhash_opslimit_interactive()
        private fun memLimit(): Int = Sodium.crypto_pwhash_memlimit_interactive()
    }
}

data class EncryptedBackupHeader(
    val version: Short = currentVersion,
    val salt: ByteArray,
    val uuidHash: ByteArray,
    val opsLimit: Int,
    val memLimit: Int
) {
    companion object {
        const val TAG = "EncryptedBackupHeader"

        private const val androidMagicNumber: String = "WBUA"
        const val currentVersion: Short = 2
        private const val saltLength = 16
        const val uuidHashLength = 32

        private const val androidMagicNumberLength = 4
        const val totalHeaderLength = androidMagicNumberLength + 1 + 2 + saltLength + uuidHashLength + 4 + 4

        fun toByteArray(header: EncryptedBackupHeader): ByteArray =
            ByteBuffer.allocate(totalHeaderLength).apply {
                put(androidMagicNumber.toByteArray())
                put(0.toByte())
                putShort(header.version)
                put(header.salt)
                put(header.uuidHash)
                putInt(header.opsLimit)
                putInt(header.memLimit)
            }.array()

        fun readEncryptedMetadata(encryptedBackup: File): EncryptedBackupHeader? =
            if (encryptedBackup.length() > totalHeaderLength) {
                val encryptedMetadataBytes = ByteArray(totalHeaderLength)
                encryptedBackup.inputStream().buffered().read(encryptedMetadataBytes)
                fromByteArray(encryptedMetadataBytes)
            } else {
                error(TAG, "Backup file header corrupted or invalid")
                null
            }

        private fun fromByteArray(bytes: ByteArray): EncryptedBackupHeader? =
            if (bytes.size == totalHeaderLength) {
                val buffer = ByteBuffer.wrap(bytes)
                val magicNumber = ByteArray(androidMagicNumberLength)
                buffer.get(magicNumber)
                if (magicNumber.map { it.toChar() }.joinToString() == androidMagicNumber) {
                    buffer.get() //skip null byte
                    val version = buffer.short
                    if (version == currentVersion) {
                        val salt = ByteArray(saltLength)
                        buffer.get(salt)
                        val uuidHash = ByteArray(uuidHashLength)
                        buffer.get(uuidHash)
                        val opslimit = buffer.int
                        val memlimit = buffer.int
                        EncryptedBackupHeader(currentVersion, salt, uuidHash, opslimit, memlimit)
                    } else {
                        error(TAG, "Unsupported backup version")
                        null
                    }
                } else {
                    error(TAG, "archive has incorrect magic number")
                    null
                }
            } else {
                error(TAG, "Invalid header length")
                null
            }
    }
}
