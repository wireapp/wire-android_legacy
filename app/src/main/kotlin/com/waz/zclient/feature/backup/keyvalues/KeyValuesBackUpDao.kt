package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.feature.backup.io.database.BatchReadableDao
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity

class KeyValuesBackUpDao(private val keyValuesDao: KeyValuesDao) : BatchReadableDao<KeyValuesEntity> {
    override suspend fun insert(item: KeyValuesEntity) = keyValuesDao.insert(item)

    override suspend fun count() = keyValuesDao.size()

    override suspend fun nextBatch(start: Int, batchSize: Int) = keyValuesDao.batch(start, batchSize)
}
