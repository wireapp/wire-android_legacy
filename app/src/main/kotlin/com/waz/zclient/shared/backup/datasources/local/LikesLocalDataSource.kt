package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity

class LikesLocalDataSource(private val likesDao: LikesDao) {
    suspend fun getAllLikes(): List<LikesEntity> = likesDao.allLikes()
}