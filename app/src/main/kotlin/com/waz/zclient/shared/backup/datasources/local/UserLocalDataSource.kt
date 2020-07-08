package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.service.UserDao

class UserLocalDataSource(private val userDao: UserDao) {
    suspend fun getAllUsers(): List<UserEntity> = userDao.allUsers()
}