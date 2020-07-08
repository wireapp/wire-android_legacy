package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.buttons.ButtonDao
import com.waz.zclient.storage.db.buttons.ButtonEntity

class ButtonLocalDataSource(private val buttonDao: ButtonDao) {
    suspend fun getAllButtons(): List<ButtonEntity> = buttonDao.allButtons()
}