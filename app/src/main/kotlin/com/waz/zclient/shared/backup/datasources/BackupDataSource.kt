package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.mapRight
import com.waz.zclient.core.utilities.IOHandler
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import java.io.File

class BackupDataSource(private val dataSources: List<BackupLocalDataSource<out Any, out Any>>) : BackupRepository {
    override fun writeAllToFiles(targetDir: File) =
        dataSources.mapRight { dataSource ->
            dataSource.withIndex().mapRight {
                IOHandler.writeTextToFile(targetDir, "${dataSource.name}_${it.index}.json") { it.value }
            }
        }.map {
            it.flatten()
        }
}
