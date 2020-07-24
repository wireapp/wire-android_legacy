package com.waz.zclient.shared.backup.handlers

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.logging.Logger.Companion.error
import com.waz.zclient.core.utilities.IOHandler
import java.io.File

interface EncryptionHandler {
    fun encryptBackup(backup: File, password: String, userId: UserId): Either<Failure, File>
}

class EncryptionHandlerImpl(private val libSodiumEncryption: LibSodiumEncryption) : EncryptionHandler {
    override fun encryptBackup(backup: File, password: String, userId: UserId) =
        IOHandler.readBytesFromFile(backup).flatMap { backupBytes ->
            val salt = libSodiumEncryption.generateSalt()
            val encryptedBytes = libSodiumEncryption.encrypt(backupBytes, password, salt)
            val meta = libSodiumEncryption.getMetaDataBytes(password, salt, userId)

            if (encryptedBytes != null && meta != null) {
                IOHandler.writeBytesToFile(backup.parentFile, backup.name + "_encrypted") {
                    meta + encryptedBytes
                }
            } else if (meta == null) {
                error(TAG, "Failed to create metadata")
                Left(EncryptionFailure("Failed to create metadata"))
            } else {
                error(TAG, "Failed to encrypt backup")
                Left(EncryptionFailure("Failed to encrypt backup"))
            }
        }

    companion object {
        data class EncryptionFailure(val msg: String) : FeatureFailure()

        const val TAG = "EncryptionHandler"
    }
}
