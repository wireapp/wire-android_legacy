package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity

class PropertiesLocalDataSource(private val propertiesDao: PropertiesDao) {
    suspend fun getAllProperties(): List<PropertiesEntity> = propertiesDao.allProperties()
}