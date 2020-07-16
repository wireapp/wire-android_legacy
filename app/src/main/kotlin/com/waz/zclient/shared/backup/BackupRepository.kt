package com.waz.zclient.shared.backup

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.functional.Either
import java.io.File

interface BackupRepository {
    suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: String, targetDir: File): Either<String, File>
}
