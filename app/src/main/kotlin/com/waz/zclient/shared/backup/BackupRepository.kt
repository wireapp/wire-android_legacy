package com.waz.zclient.shared.backup

import com.waz.model.Handle
import com.waz.model.UserId

class Password(val value: String)

interface BackupRepository {
    suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: Password)
}