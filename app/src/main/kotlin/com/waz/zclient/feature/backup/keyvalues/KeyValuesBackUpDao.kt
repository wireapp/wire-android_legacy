package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.feature.backup.io.database.SingleReadDao
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity

class KeyValuesBackUpDao(private val keyValuesDao: KeyValuesDao) : SingleReadDao<KeyValuesEntity> {
    override suspend fun insert(item: KeyValuesEntity) = keyValuesDao.insert(item)

    override suspend fun allItems(): List<KeyValuesEntity> = keyValuesDao.allKeyValues()
}
