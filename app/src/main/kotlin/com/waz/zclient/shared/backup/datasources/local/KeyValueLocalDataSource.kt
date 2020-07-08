package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity

class KeyValueLocalDataSource(private val keyValuesDao: KeyValuesDao) {
    suspend fun getAllKeyValues(): List<KeyValuesEntity> = keyValuesDao.allKeyValues()
}