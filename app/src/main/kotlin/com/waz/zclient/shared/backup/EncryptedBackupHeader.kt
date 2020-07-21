package com.waz.zclient.shared.backup

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

        fun serializeHeader(header: EncryptedBackupHeader): ByteArray {
            val buffer = ByteBuffer.allocate(totalHeaderLength)

            buffer.put(androidMagicNumber.toByteArray())
            buffer.put(0.toByte())
            buffer.putShort(header.version)
            buffer.put(header.salt)
            buffer.put(header.uuidHash)
            buffer.putInt(header.opslimit)
            buffer.putInt(header.memlimit)

            return buffer.array()
        }
    }
}
