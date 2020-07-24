package com.waz.zclient.shared.backup.handlers

import com.waz.zclient.core.logging.Logger.Companion.error
import java.nio.ByteBuffer

data class EncryptedBackupHeader(
    val version: Short = currentVersion,
    val salt: ByteArray,
    val uuidHash: ByteArray,
    val opslimit: Int,
    val memlimit: Int
) {
    companion object {
        const val TAG = "EncryptedBackupHeader"

        private const val androidMagicNumber: String = "WBUA"
        const val currentVersion: Short = 2
        private const val saltLength = 16
        const val uuidHashLength = 32

        private const val androidMagicNumberLength = 4
        private const val totalHeaderLength = androidMagicNumberLength + 1 + 2 + saltLength + uuidHashLength + 4 + 4

        fun parse(bytes: ByteArray): EncryptedBackupHeader? =
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

        fun serializeHeader(header: EncryptedBackupHeader): ByteArray =
            ByteBuffer.allocate(totalHeaderLength).apply {
                put(androidMagicNumber.toByteArray())
                put(0.toByte())
                putShort(header.version)
                put(header.salt)
                put(header.uuidHash)
                putInt(header.opslimit)
                putInt(header.memlimit)
            }.array()
    }
}
