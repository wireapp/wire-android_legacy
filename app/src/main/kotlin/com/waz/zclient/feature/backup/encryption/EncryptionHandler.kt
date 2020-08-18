package com.waz.zclient.feature.backup.encryption

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import java.io.File

interface EncryptionHandler {
    fun encrypt(backupFile: File, userId: UserId, password: String): Either<Failure, File>
    fun decrypt(backupFile: File, userId: UserId, password: String): Either<Failure, File>
}
