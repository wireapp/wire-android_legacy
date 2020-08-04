package com.waz.zclient.feature.backup.conversations.folders

import com.waz.zclient.feature.backup.io.database.SingleReadDao
import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity

class FoldersBackUpDao(private val foldersDao: FoldersDao) : SingleReadDao<FoldersEntity> {

    override suspend fun insert(item: FoldersEntity) = foldersDao.insert(item)

    override suspend fun allItems(): List<FoldersEntity> = foldersDao.allFolders()
}
