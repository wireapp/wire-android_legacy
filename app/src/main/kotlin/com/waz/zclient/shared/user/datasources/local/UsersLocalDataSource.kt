package com.waz.zclient.shared.user.datasources.local

import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.users.model.UsersEntity
import com.waz.zclient.storage.db.users.service.UsersDao
import com.waz.zclient.storage.pref.global.GlobalPreferences
import kotlinx.coroutines.flow.Flow

class UsersLocalDataSource constructor(
        private val usersDao: UsersDao,
        private val globalPreferences: GlobalPreferences
) {

    private val userId: String
        get() = globalPreferences.activeUserId

    fun profileDetails(): Flow<UsersEntity> = usersDao.byId(userId)

    suspend fun insertUser(user: UsersEntity) = requestDatabase { usersDao.insert(user) }

    suspend fun changeName(value: String) = requestDatabase { usersDao.updateName(userId, value) }

    suspend fun changeHandle(value: String) = requestDatabase { usersDao.updateHandle(userId, value) }

    suspend fun changeEmail(value: String) = requestDatabase { usersDao.updateEmail(userId, value) }

    suspend fun changePhone(value: String) = requestDatabase { usersDao.updatePhone(userId, value) }

    suspend fun deletePhone() = requestDatabase { usersDao.updatePhone(userId, String.empty()) }

    fun currentUserId() = userId

    fun setCurrentUserId(userId: String) {
        globalPreferences.activeUserId = userId
    }
}
