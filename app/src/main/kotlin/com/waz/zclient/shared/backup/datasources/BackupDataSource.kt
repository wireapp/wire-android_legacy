package com.waz.zclient.shared.backup.datasources

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.mapRight
import com.waz.zclient.core.utilities.IOHandler.writeTextToFile
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import java.io.File

class BackupDataSource(private val dataSources: List<BackupLocalDataSource<out Any, out Any>>) : BackupRepository {
    override suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: String, targetDir: File): Either<String, File> {
        TODO("Not yet implemented")
    }

    override fun writeAllToFiles(targetDir: File) =
        dataSources.mapRight { writeToFiles(targetDir, it) }.map { it.flatten() }

    private fun <Entity, JSON> writeToFiles(targetDir: File, dataSource: BackupLocalDataSource<Entity, JSON>) =
        dataSource.withIndex().mapRight {
            writeTextToFile(targetDir, "${dataSource.name}_${it.index}.json") { it.value }
        }
}
