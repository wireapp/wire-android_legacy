package com.waz.zclient.user.data.source.local


import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.service.UserDbService
import kotlinx.coroutines.flow.Flow

class UsersLocalDataSource constructor(private val userService: UserDbService,
                                       private val globalPreferences: com.waz.zclient.storage.pref.GlobalPreferences) {

    private val userId = globalPreferences.activeUserId

    suspend fun add(user: UserDao) = userService.insert(user)

    suspend fun profileDetails(): Flow<UserDao> = userService.byId(userId)

    suspend fun changeName(value: String) = userService.updateName(userId, value)

    suspend fun changeHandle(value: String) = userService.updateHandle(userId, value)

    suspend fun changeEmail(value: String) = userService.updateEmail(userId, value)

    suspend fun changePhone(value: String) = userService.updatePhone(userId, value)

}
