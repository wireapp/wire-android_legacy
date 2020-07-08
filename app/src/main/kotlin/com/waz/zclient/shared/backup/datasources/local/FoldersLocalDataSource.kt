package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity

class FoldersLocalDataSource(private val foldersDao: FoldersDao) {
    suspend fun getAllFolders(): List<FoldersEntity> = foldersDao.allFolders()
}