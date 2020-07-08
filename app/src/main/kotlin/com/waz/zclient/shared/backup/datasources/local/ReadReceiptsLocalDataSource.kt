package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity

class ReadReceiptsLocalDataSource(private val readReceiptsDao: ReadReceiptsDao) {
    suspend fun getAllReadReceipts(): List<ReadReceiptsEntity> = readReceiptsDao.allReceipts()
}