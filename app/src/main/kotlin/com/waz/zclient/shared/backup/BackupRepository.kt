package com.waz.zclient.shared.backup

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.functional.Either
import java.io.File

class Password(val value: String)

interface BackupRepository {
    suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: Password, targetDir: File): Either<String, File>
}