package com.waz.zclient.user.data.source.local

import com.waz.zclient.ContextProvider
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.pref.GlobalPreferences

class UsersLocalDataSource constructor(
    private val globalPreferences: GlobalPreferences = GlobalPreferences(ContextProvider.getApplicationContext()),
    userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), globalPreferences.activeUserId)) {

    private val userId = globalPreferences.activeUserId

    private val userService: UserDbService = userDatabase.userDbService()

    suspend fun add(user: UserDao) = userService.insert(user)

    suspend fun profile(): Either<Failure, UserDao> = requestDatabase { userService.selectById(userId) }

    suspend fun changeName(value: String) = requestDatabase { userService.updateName(userId, value) }

    suspend fun changeHandle(value: String) = requestDatabase { userService.updateHandle(userId, value) }

    suspend fun changeEmail(value: String) = requestDatabase { userService.updateEmail(userId, value) }

    suspend fun changePhone(value: String) = requestDatabase { userService.updatePhone(userId, value) }


}
