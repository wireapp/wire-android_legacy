package com.waz.zclient.shared.backup.datasources

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.flatten
import com.waz.zclient.core.functional.mapRight
import com.waz.zclient.core.logging.Logger.Companion.error
import com.waz.zclient.core.logging.Logger.Companion.verbose
import com.waz.zclient.core.logging.Logger.Companion.warn
import com.waz.zclient.core.utilities.IOUtils
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.EncryptedBackupHeader
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import org.threeten.bp.Instant
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.zip.ZipOutputStream

import org.libsodium.jni.Sodium
import org.libsodium.jni.Sodium.randombytes
import java.security.SecureRandom
import java.util.Locale

class BackupDataSource(private val dataSources: List<BackupLocalDataSource<out Any, out Any>>) : BackupRepository {
    override suspend fun exportDatabase(userId: UserId, userHandle: Handle, password: String, targetDir: File): Either<Failure, File> =
        zipData(userHandle, targetDir).flatMap { backup ->
            encryptBackup(backup, password, userId)
        }

    fun zipData(userHandle: Handle, targetDir: File): Either<Failure, File> =
        IOUtils.createTemporaryFolder(backupTempDirName(userHandle)).flatMap { tempDir ->
            writeAllToFiles(tempDir).map { jsonFiles -> Pair(tempDir, jsonFiles) }
        }.flatMap {
            val tempDir = it.first
            val jsonFiles = it.second

            val zipFile = File(targetDir, backupZipFileName(userHandle)).apply {
                deleteOnExit()
            }

            IOUtils.withResource(ZipOutputStream(FileOutputStream(zipFile))) { zip ->
                jsonFiles.mapRight { jsonFile ->
                    IOUtils.withResource(BufferedInputStream(FileInputStream(jsonFile))) {
                        IOUtils.writeZipEntry(it, zip, jsonFile.name)
                        jsonFile
                    }
                }
            }.flatten().map {
                tempDir.delete()
                zipFile
            }
        }

    override fun writeAllToFiles(targetDir: File) =
        dataSources.mapRight { dataSource ->
            dataSource.withIndex().mapRight {
                IOUtils.writeTextToFile(targetDir, "${dataSource.name}_${it.index}.json") { it.value }
            }
        }.map { it.flatten() }

    private fun encryptBackup(backup: File, password: String, userId: UserId): Either<Failure, File> =
        IOUtils.readBytesFromFile(backup).flatMap { backupBytes ->
            val salt = generateSalt()
            val encryptedBytes = encrypt(backupBytes, password, salt)
            val meta = getMetaDataBytes(password, salt, userId)

            if (encryptedBytes != null && meta != null) {
                IOUtils.writeBytesToFile(backup.parentFile, backup.name + "_encrypted") {
                    meta + encryptedBytes
                }.map {
                    it.deleteOnExit()
                    backup.delete()
                    it.renameTo(backup)
                    File(backup.path)
                }
            } else if (meta == null) {
                error(TAG, "Failed to create metadata")
                Either.Left(BackupFailure("Failed to create metadata"))
            } else {
                error(TAG, "Failed to encrypt backup")
                Either.Left(BackupFailure("Failed to encrypt backup"))
            }
        }

    companion object {
        data class BackupFailure(val msg: String) : FeatureFailure()

        const val TAG = "BackupDataSource"

        @SuppressWarnings("MagicNumber")
        fun timestamp(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().getEpochSecond() * 1000).toString()

        fun backupTempDirName(userHandle: Handle): String = "Wire-${userHandle.string()}-Backup_${timestamp()}"

        fun backupZipFileName(userHandle: Handle): String = "Wire-${userHandle.string()}-Backup_${timestamp()}.android_wbu"

        private val defOpsLimit = Sodium.crypto_pwhash_opslimit_interactive()
        private val defMemLimit = Sodium.crypto_pwhash_memlimit_interactive()

        fun hash(input: String, salt: ByteArray, opslimit: Int = defOpsLimit, memlimit: Int = defMemLimit): ByteArray? {
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

        fun generateSalt(): ByteArray {
            val count = Sodium.crypto_pwhash_saltbytes()
            val buffer = ByteArray(count)

            when (loadLibrary) {
                is Either.Right -> randombytes(buffer, count)
                is Either.Left -> {
                    warn(TAG, "Libsodium failed to generate $count random bytes. Falling back to SecureRandom")
                    secureRandom.nextBytes(buffer)
                }
            }

            return buffer
        }

        //Got this magic number from https://github.com/joshjdevl/libsodium-jni/blob/master/src/test/java/org/libsodium/jni/crypto/SecretStreamTest.java#L48
        private const val stateByteArraySize = 52

        fun initializeState(key: ByteArray, header: ByteArray, init: (ByteArray, ByteArray, ByteArray) -> Int): ByteArray? =
            if (header.size != Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()) {
                error(TAG, "Invalid header length")
                null
            } else if (key.size != Sodium.crypto_secretstream_xchacha20poly1305_keybytes()) {
                error(TAG, "Invalid key length")
                null
            } else {
                val state = ByteArray(stateByteArraySize)
                val res = init(state, header, key)
                if (res != 0) {
                    error(TAG, "error whilst initializing push")
                    null
                } else {
                    state
                }
            }

        fun initPush(key: ByteArray, header: ByteArray): ByteArray? = initializeState(key, header) {
            s: ByteArray, h: ByteArray, k: ByteArray -> Sodium.crypto_secretstream_xchacha20poly1305_init_push(s, h, k)
        }

        fun encrypt(
            msg: ByteArray,
            password: String,
            salt: ByteArray,
            opsLimit: Int = defOpsLimit,
            memLimit: Int = defMemLimit
        ): ByteArray? {
            val key = hash(password, salt, opsLimit, memLimit)

            return if (key != null) {
                val expectedKeySize = Sodium.crypto_aead_chacha20poly1305_keybytes()
                if (key.size != expectedKeySize) {
                    verbose(TAG, "Key length invalid: ${key.size} did not match $expectedKeySize")
                }

                val header = ByteArray(streamHeaderLength)
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
                        error(TAG, "Failed to hash backup")
                        null
                    }
                } else {
                    error(TAG, "Failed to init encrypt")
                    null
                }
            } else {
                error(TAG, "Couldn't derive key from password")
                null
            }
        }

        //This method returns the metadata in the format described here:
        //https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history.md
        fun getMetaDataBytes(password: String, salt: ByteArray, userId: UserId): ByteArray? {
            val uuidHash = hash(userId.str(), salt)
            return if (uuidHash != null && uuidHash.size == EncryptedBackupHeader.uuidHashLength) {
                val header = EncryptedBackupHeader(EncryptedBackupHeader.currentVersion, salt, uuidHash, defOpsLimit, defMemLimit)
                EncryptedBackupHeader.serializeHeader(header)
            } else if (uuidHash != null) {
                error(TAG, "uuidHash length invalid, expected: ${EncryptedBackupHeader.uuidHashLength}, got: ${uuidHash.size}")
                null
            } else {
                error(TAG, "Failed to hash account id for backup")
                null
            }
        }

        private val loadLibrary: Either<Failure, Unit> by lazy {
            try {
                System.loadLibrary("sodium")
                System.loadLibrary("randombytes")
                Either.Right(Unit)
            } catch (ex: UnsatisfiedLinkError) {
                Either.Left(GenericUseCaseError(ex))
            }
        }

        private val secureRandom: SecureRandom by lazy { SecureRandom() }

        private val streamHeaderLength = Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()
    }
}
