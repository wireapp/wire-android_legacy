package com.waz.zclient.shared.backup.usecase

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.handlers.EncryptionHandler
import com.waz.zclient.shared.backup.handlers.ZipBackupHandler
import java.io.File

class BackupUseCase(
    private val backupRepository: BackupRepository,
    private val zipBackupHandler: ZipBackupHandler,
    private val encryptionHandler: EncryptionHandler
) {
    fun exportDatabase(userId: UserId, userHandle: Handle, password: String, targetDir: File): Either<Failure, File> =
        backupRepository.writeAllToFiles(targetDir).flatMap { files ->
           zipBackupHandler.zipData(userHandle, targetDir, files).flatMap { backup ->
               encryptionHandler.encryptBackup(backup, password, userId).map {
                   it.deleteOnExit()
                   backup.delete()
                   it.renameTo(backup)
                   File(backup.path)
               }
           }
        }
}
