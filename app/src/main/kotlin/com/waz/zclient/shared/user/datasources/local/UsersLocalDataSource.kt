package com.waz.zclient.shared.user.datasources.local

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
}
