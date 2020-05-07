package com.waz.zclient.shared.user.datasources.local

import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.service.UserDao
import com.waz.zclient.storage.pref.global.GlobalPreferences
import kotlinx.coroutines.flow.Flow

class UsersLocalDataSource constructor(
    private val userDao: UserDao,
    private val globalPreferences: GlobalPreferences
) {

    private val userId: String
        get() = globalPreferences.activeUserId

    fun profileDetails(): Flow<UserEntity> = userDao.byId(userId)

    suspend fun insertUser(user: UserEntity) = requestDatabase { userDao.insert(user) }

    suspend fun changeName(value: String) = requestDatabase { userDao.updateName(userId, value) }

    suspend fun changeHandle(value: String) = requestDatabase { userDao.updateHandle(userId, value) }

    suspend fun changeEmail(value: String) = requestDatabase { userDao.updateEmail(userId, value) }

    suspend fun changePhone(value: String) = requestDatabase { userDao.updatePhone(userId, value) }

    suspend fun deletePhone() = requestDatabase { userDao.updatePhone(userId, String.empty()) }

    fun currentUserId() = userId

    fun setCurrentUserId(userId: String) {
        globalPreferences.activeUserId = userId
    }
}
