package com.waz.zclient.shared.backup

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.core.utilities.IOHandler
import org.libsodium.jni.Sodium
import java.io.File
import java.security.SecureRandom

interface EncryptionHandler {
    fun encryptBackup(backup: File, password: String, userId: UserId): Either<Failure, File>

    fun getOpsLimit(): Int = Sodium.crypto_pwhash_opslimit_interactive()
    fun getMemLimit(): Int = Sodium.crypto_pwhash_memlimit_interactive()

    fun hash(input: String, salt: ByteArray, opslimit: Int = getOpsLimit(), memlimit: Int = getMemLimit()): ByteArray?
    fun generateSalt(): ByteArray
    fun encrypt(msg: ByteArray, password: String, salt: ByteArray, opsLimit: Int = getOpsLimit(), memLimit: Int = getMemLimit()): ByteArray?
    fun getMetaDataBytes(password: String, salt: ByteArray, userId: UserId): ByteArray?
}

class EncryptionHandlerImpl : EncryptionHandler {
    override fun encryptBackup(backup: File, password: String, userId: UserId) =
        IOHandler.readBytesFromFile(backup).flatMap { backupBytes ->
            val salt = generateSalt()
            val encryptedBytes = encrypt(backupBytes, password, salt)
            val meta = getMetaDataBytes(password, salt, userId)

            if (encryptedBytes != null && meta != null) {
                IOHandler.writeBytesToFile(backup.parentFile, backup.name + "_encrypted") {
                    meta + encryptedBytes
                }
            } else if (meta == null) {
                Logger.error(TAG, "Failed to create metadata")
                Either.Left(EncryptionFailure("Failed to create metadata"))
            } else {
                Logger.error(TAG, "Failed to encrypt backup")
                Either.Left(EncryptionFailure("Failed to encrypt backup"))
            }
        }

    override fun hash(input: String, salt: ByteArray, opslimit: Int, memlimit: Int): ByteArray? {
        val outputLength = Sodium.crypto_secretstream_xchacha20poly1305_keybytes()
        val output = ByteArray(outputLength)
        val passBytes = input.toByteArray()
        val ret: Int = Sodium.crypto_pwhash(
                output,
                output.size,
                passBytes,
                passBytes.size,
                salt,
                opslimit,
                memlimit,
                Sodium.crypto_pwhash_alg_default()
        )

        return if (ret == 0) output else null
    }

    override fun generateSalt(): ByteArray {
        val count = Sodium.crypto_pwhash_saltbytes()
        val buffer = ByteArray(count)

        when (loadLibrary) {
            is Either.Right -> Sodium.randombytes(buffer, count)
            is Either.Left -> {
                Logger.warn(TAG, "Libsodium failed to generate $count random bytes. Falling back to SecureRandom")
                secureRandom.nextBytes(buffer)
            }
        }

        return buffer
    }

    private fun initializeState(key: ByteArray, header: ByteArray, init: (ByteArray, ByteArray, ByteArray) -> Int): ByteArray? =
        if (header.size != Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()) {
            Logger.error(TAG, "Invalid header length")
            null
        } else if (key.size != Sodium.crypto_secretstream_xchacha20poly1305_keybytes()) {
            Logger.error(TAG, "Invalid key length")
            null
        } else {
            val state = ByteArray(stateByteArraySize)
            val res = init(state, header, key)
            if (res != 0) {
                Logger.error(TAG, "error whilst initializing push")
                null
            } else {
                state
            }
        }

    private fun initPush(key: ByteArray, header: ByteArray): ByteArray? = initializeState(key, header) {
        s: ByteArray, h: ByteArray, k: ByteArray -> Sodium.crypto_secretstream_xchacha20poly1305_init_push(s, h, k)
    }

    override fun encrypt(msg: ByteArray, password: String, salt: ByteArray, opsLimit: Int, memLimit: Int): ByteArray? {
        val key = hash(password, salt, opsLimit, memLimit)

        return if (key != null) {
            val expectedKeySize = Sodium.crypto_aead_chacha20poly1305_keybytes()
            if (key.size != expectedKeySize) {
                Logger.verbose(TAG, "Key length invalid: ${key.size} did not match $expectedKeySize")
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
                    header + cipherText
                } else {
                    Logger.error(TAG, "Failed to hash backup")
                    null
                }
            } else {
                Logger.error(TAG, "Failed to init encrypt")
                null
            }
        } else {
            Logger.error(TAG, "Couldn't derive key from password")
            null
        }
    }

    //This method returns the metadata in the format described here:
    //https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history.md
    override fun getMetaDataBytes(password: String, salt: ByteArray, userId: UserId): ByteArray? {
        val uuidHash = hash(userId.str(), salt)
        return if (uuidHash != null && uuidHash.size == EncryptedBackupHeader.uuidHashLength) {
            val header = EncryptedBackupHeader(EncryptedBackupHeader.currentVersion, salt, uuidHash, getOpsLimit(), getMemLimit())
            EncryptedBackupHeader.serializeHeader(header)
        } else if (uuidHash != null) {
            Logger.error(TAG, "uuidHash length invalid, expected: ${EncryptedBackupHeader.uuidHashLength}, got: ${uuidHash.size}")
            null
        } else {
            Logger.error(TAG, "Failed to hash account id for backup")
            null
        }
    }

    companion object {
        data class EncryptionFailure(val msg: String) : FeatureFailure()

        const val TAG = "EncryptionHandler"

        //Got this magic number from https://github.com/joshjdevl/libsodium-jni/blob/master/src/test/java/org/libsodium/jni/crypto/SecretStreamTest.java#L48
        private const val stateByteArraySize = 52

        private val secureRandom: SecureRandom by lazy { SecureRandom() }

        private val loadLibrary: Either<Failure, Unit> by lazy {
            try {
                System.loadLibrary("sodium")
                System.loadLibrary("randombytes")
                Either.Right(Unit)
            } catch (ex: UnsatisfiedLinkError) {
                Either.Left(GenericUseCaseError(ex))
            }
        }
    }
}