package com.waz.zclient.user.data.source.local


import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.pref.GlobalPreferences

class UsersLocalDataSource constructor(private val userService: UserDbService,
                                       private val globalPreferences: GlobalPreferences) {

    private val userId = globalPreferences.activeUserId

    suspend fun add(user: UserDao) = userService.insert(user)

    suspend fun profile() = requestDatabase { userService.selectById(userId) }

    suspend fun changeName(value: String) = requestDatabase { userService.updateName(userId, value) }

    suspend fun changeHandle(value: String) = requestDatabase { userService.updateHandle(userId, value) }

    suspend fun changeEmail(value: String) = requestDatabase { userService.updateEmail(userId, value) }

    suspend fun changePhone(value: String) = requestDatabase { userService.updatePhone(userId, value) }


}
